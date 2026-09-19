// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.data

import br.com.colman.palavramento.domain.protocol.AuthTokens
import br.com.colman.palavramento.domain.protocol.LoginRequest
import br.com.colman.palavramento.domain.protocol.RefreshRequest
import br.com.colman.palavramento.domain.protocol.RegisterRequest
import br.com.colman.palavramento.network.RestApi
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** How long before [AuthTokens.accessTokenExpiresAt] a caller proactively refreshes (task brief 4). */
private const val RefreshMarginMs = 60_000L

/**
 * Owns the guest-or-registered session for the whole app (dossier 8, task brief 4): bootstraps a
 * guest on first launch, refreshes the access token before it expires or after a 401, recovers from
 * a rejected refresh token (a new guest either way, plus [sessionExpired] for a registered player),
 * and runs registration/login (guest promotion and login migration, ADR 0007).
 *
 * [nowMs] is injected (task brief pattern also used by [br.com.colman.palavramento.clock.ServerClock])
 * so [needsRefresh] is unit-testable without waiting on a real clock.
 */
class AuthController(
  private val restApi: RestApi,
  private val tokenRepository: TokenRepository,
  private val profileRepository: ProfileRepository,
  private val historyRepository: HistoryRepository,
  private val nowMs: () -> Long = System::currentTimeMillis,
) {

  /**
   * Serializes every read, refresh and save of the session. The server rotates refresh tokens and
   * treats a second use of the same one as theft, revoking every token the player has (ADR 0007).
   * Two callers refreshing at once, like the lobby's init and ON_RESUME syncs, would do exactly that
   * and sign a registered player out.
   */
  private val sessionMutex = Mutex()

  /** The stored session as it changes: guest bootstrap, login, logout, expiry recovery. */
  val session: Flow<AuthTokens?> = tokenRepository.tokens

  /** True once a registered player's session was rejected, until they sign in again or dismiss it. */
  val sessionExpired: Flow<Boolean> = tokenRepository.sessionExpired

  /** The current tokens, creating a fresh guest session if none exist yet (dossier 8). */
  suspend fun bootstrap(): AuthTokens? =
    sessionMutex.withLock { tokenRepository.tokens.first() ?: restApi.newGuest(tokenRepository) }

  /**
   * A live access token: the stored one if it is not close to expiring, otherwise refreshed first
   * (task brief 4: "refresh... before expiry"). Null only when there is no session and a guest could
   * not be created; every other failure falls back to the last known (possibly stale) token so a
   * caller can still try the network and fail on its own terms.
   */
  suspend fun validAccessToken(): String? = sessionMutex.withLock { currentAccessToken() }

  /**
   * Runs [request] with a valid access token, refreshing once and retrying on a 401 (task brief 4:
   * "refresh through /auth/refresh on 401... with retry"). Returns null when no token could be
   * obtained at all or the retried call still failed.
   */
  @Suppress("ReturnCount") // guard-clause style: each early return is one distinct outcome (matches AuthService.kt).
  suspend fun <T> callAuthenticated(request: suspend (accessToken: String) -> T): T? {
    val token = validAccessToken() ?: return null
    val firstAttempt = runCatching { request(token) }
    firstAttempt.onSuccess { return it }
    if (!firstAttempt.isUnauthorized()) return null

    // Another caller may have replaced the session while [request] was in flight: retry with theirs
    // rather than refreshing again with a refresh token the server has already revoked.
    val retriedToken = sessionMutex.withLock {
      val current = tokenRepository.tokens.first()
      if (current == null || current.accessToken != token) current?.accessToken else refreshOrRecover(current)
    } ?: return null
    return runCatching { request(retriedToken) }.getOrNull()
  }

  /** Promotes the current guest (same player, history kept) or registers a brand new account. */
  suspend fun register(email: String, password: String, displayName: String): AuthCallResult = signIn(
    request = { guestToken -> restApi.register(RegisterRequest(email, password, displayName), guestToken) },
    failureFor = { status ->
      when (status) {
        HttpStatusCode.Conflict -> AuthCallResult.EmailTaken
        HttpStatusCode.Unauthorized -> AuthCallResult.GuestSessionNotFound
        else -> AuthCallResult.NetworkError
      }
    },
  )

  /** Logs into an existing account, migrating the current guest's history into it if there is one. */
  suspend fun login(email: String, password: String): AuthCallResult = signIn(
    request = { guestToken -> restApi.login(LoginRequest(email, password), guestToken) },
    failureFor = { status ->
      if (status == HttpStatusCode.Unauthorized) AuthCallResult.InvalidCredentials else AuthCallResult.NetworkError
    },
  )

  /** Clears the session and cache, then bootstraps a brand new guest (task brief 4: "Logout returns a fresh guest"). */
  suspend fun logout() {
    sessionMutex.withLock {
      tokenRepository.clear()
      clearLocalCache()
      restApi.newGuest(tokenRepository)
    }
  }

  /** Hides the [sessionExpired] notice for a player who chose to keep playing as a guest. */
  suspend fun dismissSessionExpired() = tokenRepository.setSessionExpired(false)

  /**
   * Runs a register/login [request] with the current guest's access token, if the session is a
   * guest's. That token is refreshed first when close to expiring: the server answers an expired
   * bearer with 401 even on those optional-auth routes, which would read as wrong credentials and
   * skip the guest's promotion or migration.
   */
  private suspend fun signIn(
    request: suspend (guestToken: String?) -> AuthTokens,
    failureFor: (HttpStatusCode?) -> AuthCallResult,
  ): AuthCallResult = sessionMutex.withLock {
    val guestToken = tokenRepository.tokens.first()?.takeIf { it.isGuest }?.let { currentAccessToken() }
    runCatching { request(guestToken) }.fold(
      onSuccess = { tokens ->
        tokenRepository.save(tokens)
        tokenRepository.setSessionExpired(false)
        // The signed-in identity just changed (a new player id for a login migration, or guest -> not
        // guest for a promotion): drop the cache instead of showing a stale mix until the next sync.
        clearLocalCache()
        AuthCallResult.Success
      },
      onFailure = { failureFor(it.responseStatus()) },
    )
  }

  /** [validAccessToken]'s body, for callers already holding [sessionMutex]. */
  private suspend fun currentAccessToken(): String? {
    val current = tokenRepository.tokens.first() ?: return restApi.newGuest(tokenRepository)?.accessToken
    return if (current.needsRefresh(nowMs())) refreshOrRecover(current) else current.accessToken
  }

  /**
   * Refreshes [current]. When the server rejects its refresh token, the player carries on as a brand
   * new guest (task brief 4). A registered player is also flagged with [sessionExpired] so the lobby
   * asks them to log in again; logging in from that guest migrates whatever they played meanwhile
   * (ADR 0007).
   */
  private suspend fun refreshOrRecover(current: AuthTokens): String? {
    // Once the server has rotated the refresh token, its replacement must reach storage even if the
    // caller is cancelled meanwhile: the old one is revoked, and presenting it again ends the session.
    val refreshed = withContext(NonCancellable) {
      runCatching { restApi.refresh(RefreshRequest(current.refreshToken)) }.onSuccess { tokenRepository.save(it) }
    }
    return when {
      refreshed.isSuccess -> refreshed.getOrThrow().accessToken
      // A network/server hiccup, not a rejected token: keep the last known access token so the
      // caller's own request (or the WS reconnect loop) can still try and fail on its own terms.
      !refreshed.isUnauthorized() -> current.accessToken
      else -> {
        tokenRepository.clear()
        clearLocalCache()
        if (!current.isGuest) tokenRepository.setSessionExpired(true)
        restApi.newGuest(tokenRepository)?.accessToken
      }
    }
  }

  private suspend fun clearLocalCache() {
    profileRepository.clear()
    historyRepository.clear()
  }
}

/** A brand new guest session (`POST /auth/guest`, dossier 8), saved as the current one; null when offline. */
private suspend fun RestApi.newGuest(tokenRepository: TokenRepository): AuthTokens? =
  runCatching { guestAuth() }.getOrNull()?.also { tokenRepository.save(it) }

private fun AuthTokens.needsRefresh(now: Long): Boolean = now >= accessTokenExpiresAt - RefreshMarginMs

/** Outcome of [AuthController.register]/[AuthController.login], for pt-BR UI mapping (task brief 4). */
sealed interface AuthCallResult {
  data object Success : AuthCallResult
  data object EmailTaken : AuthCallResult
  data object InvalidCredentials : AuthCallResult
  data object GuestSessionNotFound : AuthCallResult
  data object NetworkError : AuthCallResult
}

private fun Result<*>.isUnauthorized(): Boolean = exceptionOrNull()?.responseStatus() == HttpStatusCode.Unauthorized

private fun Throwable.responseStatus(): HttpStatusCode? = (this as? ResponseException)?.response?.status
