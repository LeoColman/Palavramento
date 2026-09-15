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
import kotlinx.coroutines.flow.first

/** How long before [AuthTokens.accessTokenExpiresAt] a caller proactively refreshes (task brief 4). */
private const val RefreshMarginMs = 60_000L

/**
 * Owns the guest-or-registered session for the whole app (dossier 8, task brief 4): bootstraps a
 * guest on first launch, refreshes the access token before it expires or after a 401, recovers from
 * a rejected refresh token (a new guest for a guest, a login prompt for a registered player), and
 * runs registration/login (guest promotion and login migration, ADR 0007).
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

  /** The current tokens, creating a fresh guest session if none exist yet (dossier 8). */
  suspend fun bootstrap(): AuthTokens? = tokenRepository.tokens.first() ?: createGuest()

  /**
   * A live access token: the stored one if it is not close to expiring, otherwise refreshed first
   * (task brief 4: "refresh... before expiry"). Null only when a registered player's refresh token
   * was rejected and they need to log in again; every other failure falls back to the last known
   * (possibly stale) token so a caller can still try the network and fail on its own terms.
   */
  suspend fun validAccessToken(): String? {
    val current = tokenRepository.tokens.first() ?: return createGuest()?.accessToken
    return if (current.needsRefresh(nowMs())) refreshOrRecover(current) else current.accessToken
  }

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

    val current = tokenRepository.tokens.first() ?: return null
    val retriedToken = refreshOrRecover(current) ?: return null
    return runCatching { request(retriedToken) }.getOrNull()
  }

  /** Promotes the current guest (same player, history kept) or registers a brand new account. */
  suspend fun register(email: String, password: String, displayName: String): AuthCallResult {
    val guestToken = currentGuestToken(tokenRepository)
    val result = runCatching { restApi.register(RegisterRequest(email, password, displayName), guestToken) }
    return result.fold(
      onSuccess = { onAuthSuccess(it) },
      onFailure = { failure ->
        when (failure.responseStatus()) {
          HttpStatusCode.Conflict -> AuthCallResult.EmailTaken
          HttpStatusCode.Unauthorized -> AuthCallResult.GuestSessionNotFound
          else -> AuthCallResult.NetworkError
        }
      },
    )
  }

  /** Logs into an existing account, migrating the current guest's history into it if there is one. */
  suspend fun login(email: String, password: String): AuthCallResult {
    val guestToken = currentGuestToken(tokenRepository)
    val result = runCatching { restApi.login(LoginRequest(email, password), guestToken) }
    return result.fold(
      onSuccess = { onAuthSuccess(it) },
      onFailure = { failure ->
        when (failure.responseStatus()) {
          HttpStatusCode.Unauthorized -> AuthCallResult.InvalidCredentials
          else -> AuthCallResult.NetworkError
        }
      },
    )
  }

  /** Clears the session and cache, then bootstraps a brand new guest (task brief 4: "Logout returns a fresh guest"). */
  suspend fun logout() {
    tokenRepository.clear()
    clearLocalCache()
    createGuest()
  }

  private suspend fun onAuthSuccess(tokens: AuthTokens): AuthCallResult {
    tokenRepository.save(tokens)
    // The signed-in identity just changed (a new player id for a login migration, or guest -> not
    // guest for a promotion): drop the cache instead of showing a stale mix until the next sync.
    clearLocalCache()
    return AuthCallResult.Success
  }

  private suspend fun createGuest(): AuthTokens? =
    runCatching { restApi.guestAuth() }.getOrNull()?.also { tokenRepository.save(it) }

  private suspend fun refreshOrRecover(current: AuthTokens): String? {
    val refreshed = runCatching { restApi.refresh(RefreshRequest(current.refreshToken)) }
    refreshed.onSuccess {
      tokenRepository.save(it)
      return it.accessToken
    }
    return if (refreshed.isUnauthorized()) {
      recoverFromInvalidRefreshToken(current)
    } else {
      // A network/server hiccup, not a rejected token: keep the last known access token so the
      // caller's own request (or the WS reconnect loop) can still try and fail on its own terms.
      current.accessToken
    }
  }

  private suspend fun recoverFromInvalidRefreshToken(current: AuthTokens): String? = if (current.isGuest) {
    // task brief 4: "if the refresh token is rejected for a guest, create a new guest".
    clearLocalCache()
    createGuest()?.accessToken
  } else {
    // task brief 4: "for a registered player, ask them to log in again".
    tokenRepository.clear()
    clearLocalCache()
    null
  }

  private suspend fun clearLocalCache() {
    profileRepository.clear()
    historyRepository.clear()
  }
}

private fun AuthTokens.needsRefresh(now: Long): Boolean = now >= accessTokenExpiresAt - RefreshMarginMs

private suspend fun currentGuestToken(tokenRepository: TokenRepository): String? =
  tokenRepository.tokens.first()?.takeIf { it.isGuest }?.accessToken

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
