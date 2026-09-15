// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.auth

import br.com.colman.palavramento.domain.protocol.AuthTokens
import br.com.colman.palavramento.server.config.ServerConfig
import br.com.colman.palavramento.server.repository.GuestAuthProvider
import br.com.colman.palavramento.server.repository.PasswordAuthProvider
import br.com.colman.palavramento.server.repository.PlayerRepository
import br.com.colman.palavramento.server.repository.PlayerRow
import br.com.colman.palavramento.server.repository.PlayerStatsRepository
import br.com.colman.palavramento.server.repository.PlayerStatsRow
import br.com.colman.palavramento.server.repository.RefreshTokenRepository
import br.com.colman.palavramento.server.repository.RefreshTokenRow
import br.com.colman.palavramento.server.repository.RoundResultRepository
import br.com.colman.palavramento.server.repository.SubmissionRepository
import br.com.colman.palavramento.server.round.GameClock
import java.util.UUID

/** Outcome of [AuthService.refresh]. */
sealed interface RefreshOutcome {
  data class Success(val tokens: AuthTokens) : RefreshOutcome
  data object Invalid : RefreshOutcome
}

/** Outcome of [AuthService.register]. */
sealed interface RegisterOutcome {
  data class Success(val tokens: AuthTokens) : RegisterOutcome
  data object EmailTaken : RegisterOutcome
  data object GuestNotFound : RegisterOutcome
  data object NotAGuest : RegisterOutcome
}

/** Outcome of [AuthService.login]. */
sealed interface LoginOutcome {
  data class Success(val tokens: AuthTokens) : LoginOutcome
  data object InvalidCredentials : LoginOutcome
}

/**
 * Guest/register/login/refresh (dossier §8, REST contract in `Rest.kt`), including guest promotion
 * and login migration (ADR 0007). A single class rather than one per endpoint: promotion and
 * migration both need the same "issue a fresh token pair" and "rebuild player_stats" building
 * blocks as plain guest auth and refresh do.
 */
@Suppress("LongParameterList") // every parameter is a distinct injected singleton (Koin wiring), not a group to bundle.
class AuthService(
  private val config: ServerConfig,
  private val clock: GameClock,
  private val jwtService: JwtService,
  private val playerRepository: PlayerRepository,
  private val refreshTokenRepository: RefreshTokenRepository,
  private val submissionRepository: SubmissionRepository,
  private val roundResultRepository: RoundResultRepository,
  private val playerStatsRepository: PlayerStatsRepository,
) {

  suspend fun guest(displayName: String?): AuthTokens {
    val id = UUID.randomUUID().toString()
    val name = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: defaultGuestName(id)
    playerRepository.insert(
      PlayerRow(
        id,
        name,
        isGuest = true,
        authProvider = GuestAuthProvider,
        email = null,
        passwordHash = null,
        createdAt = clock.now()
      ),
    )
    return issueTokens(id, name, isGuest = true)
  }

  /**
   * Rotates a refresh token (ADR 0007): the presented token is revoked and linked to its
   * replacement. Presenting an already-revoked token again means it was reused (e.g. stolen and
   * replayed after the legitimate client already rotated it), so every token for that player is
   * revoked in response instead of trusting it.
   */
  @Suppress("ReturnCount") // guard-clause style: each early return is one distinct invalid-token reason.
  suspend fun refresh(rawRefreshToken: String): RefreshOutcome {
    val hash = TokenHasher.hash(rawRefreshToken)
    val stored = refreshTokenRepository.findByHash(hash) ?: return RefreshOutcome.Invalid
    val now = clock.now()

    if (stored.revokedAt != null) {
      refreshTokenRepository.revokeAllForPlayer(stored.playerId, now)
      return RefreshOutcome.Invalid
    }
    if (stored.expiresAt.isBefore(now)) return RefreshOutcome.Invalid

    val player = playerRepository.findById(stored.playerId) ?: return RefreshOutcome.Invalid
    val tokens = issueTokens(player.id, player.displayName, player.isGuest)
    refreshTokenRepository.revoke(hash, TokenHasher.hash(tokens.refreshToken), now)
    return RefreshOutcome.Success(tokens)
  }

  /** Promotes the guest identified by [guestPlayerId] (dossier §8), or registers a brand new account. */
  @Suppress("ReturnCount") // guard-clause style: each early return is one distinct outcome.
  suspend fun register(guestPlayerId: String?, email: String, password: String, displayName: String): RegisterOutcome {
    if (playerRepository.findByEmail(email) != null) return RegisterOutcome.EmailTaken
    val passwordHash = PasswordHasher.hash(password)

    if (guestPlayerId == null) {
      val id = UUID.randomUUID().toString()
      playerRepository.insert(
        PlayerRow(
          id,
          displayName,
          isGuest = false,
          authProvider = PasswordAuthProvider,
          email = email,
          passwordHash = passwordHash,
          createdAt = clock.now()
        ),
      )
      return RegisterOutcome.Success(issueTokens(id, displayName, isGuest = false))
    }

    val guest = playerRepository.findById(guestPlayerId) ?: return RegisterOutcome.GuestNotFound
    if (!guest.isGuest) return RegisterOutcome.NotAGuest
    playerRepository.promote(guest.id, email, passwordHash, displayName)
    rebuildPlayerStats(guest.id)
    return RegisterOutcome.Success(issueTokens(guest.id, displayName, isGuest = false))
  }

  /**
   * Logs in an existing account. When called with a guest's access token ([guestPlayerId] not
   * null), that guest's history migrates into the target account first (dossier §8, conflict rules
   * in ADR 0007).
   */
  @Suppress("ReturnCount") // guard-clause style: each early return is one distinct invalid-credentials reason.
  suspend fun login(guestPlayerId: String?, email: String, password: String): LoginOutcome {
    val target = playerRepository.findByEmail(email) ?: return LoginOutcome.InvalidCredentials
    val hash = target.passwordHash ?: return LoginOutcome.InvalidCredentials
    if (!PasswordHasher.verify(password, hash)) return LoginOutcome.InvalidCredentials

    if (guestPlayerId != null && guestPlayerId != target.id) {
      migrateGuestInto(guestPlayerId, target.id)
    }
    return LoginOutcome.Success(issueTokens(target.id, target.displayName, isGuest = false))
  }

  /**
   * Merges [guestId]'s rounds into [targetId] (ADR 0007's migration conflict rule): for a round
   * both played, the target's own result wins and the guest's is dropped; every other round moves
   * over intact. The guest row is deleted once nothing references it anymore.
   */
  private suspend fun migrateGuestInto(guestId: String, targetId: String) {
    val guest = playerRepository.findById(guestId) ?: return
    if (!guest.isGuest) return

    val guestResults = roundResultRepository.findByPlayerAll(guestId)
    val targetRoundIds = roundResultRepository.findByPlayerAll(targetId).map { it.roundId }.toSet()

    playerRepository.transaction {
      guestResults.forEach { result ->
        if (result.roundId in targetRoundIds) {
          roundResultRepository.deleteRound(this, result.roundId, guestId)
          submissionRepository.deleteRound(this, result.roundId, guestId)
        } else {
          roundResultRepository.reassignRound(this, result.roundId, guestId, targetId)
          submissionRepository.reassignRound(this, result.roundId, guestId, targetId)
        }
      }
    }

    // Deleted outright, not just revoked: a revoked-but-present row would still violate the
    // `refresh_tokens.player_id` foreign key once the guest's player row is removed below.
    refreshTokenRepository.deleteAllForPlayer(guestId)
    playerRepository.delete(guestId)
    rebuildPlayerStats(targetId)
  }

  /** Recomputes `player_stats` from `round_results`/`submissions` (dossier §8, ADR 0007). */
  private suspend fun rebuildPlayerStats(playerId: String) {
    val results = roundResultRepository.findByPlayerAll(playerId)
    if (results.isEmpty()) {
      playerStatsRepository.delete(playerId)
      return
    }

    var totalScore = 0L
    var totalWords = 0L
    var bestGameScore = 0
    var bestWord: String? = null
    var bestWordScore = 0
    var gamesCompleted = 0
    var bestRank: Int? = null
    var totalXp = 0L

    for (result in results) {
      totalScore += result.score
      totalWords += result.words
      bestGameScore = maxOf(bestGameScore, result.score)
      if (result.words > 0) gamesCompleted++
      bestRank = bestRank?.let { minOf(it, result.rank) } ?: result.rank
      totalXp += result.xp

      val topWord = submissionRepository.findByRoundAndPlayer(result.roundId, playerId).maxByOrNull { it.score }
      if (topWord != null && topWord.score > bestWordScore) {
        bestWordScore = topWord.score
        bestWord = topWord.display
      }
    }

    playerStatsRepository.replace(
      PlayerStatsRow(
        playerId = playerId,
        totalScore = totalScore,
        totalWords = totalWords,
        bestGameScore = bestGameScore,
        bestWord = bestWord,
        bestWordScore = bestWordScore,
        gamesPlayed = results.size,
        gamesCompleted = gamesCompleted,
        bestRank = bestRank,
        totalXp = totalXp,
      ),
    )
  }

  private suspend fun issueTokens(playerId: String, displayName: String, isGuest: Boolean): AuthTokens {
    val access = jwtService.createAccessToken(playerId, isGuest)
    val rawRefresh = TokenHasher.newRawToken()
    val now = clock.now()
    refreshTokenRepository.insert(
      RefreshTokenRow(
        id = UUID.randomUUID().toString(),
        playerId = playerId,
        tokenHash = TokenHasher.hash(rawRefresh),
        createdAt = now,
        expiresAt = now.plusMillis(config.refreshTokenTtl.inWholeMilliseconds),
        revokedAt = null,
        replacedByHash = null,
      ),
    )
    return AuthTokens(
      playerId = playerId,
      displayName = displayName,
      isGuest = isGuest,
      accessToken = access.token,
      accessTokenExpiresAt = access.expiresAt.toEpochMilli(),
      refreshToken = rawRefresh,
    )
  }

  private fun defaultGuestName(playerId: String): String =
    "Convidado ${playerId.take(GuestSuffixLength).uppercase()}"

  private companion object {
    const val GuestSuffixLength = 4
  }
}
