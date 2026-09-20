// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.protocol

import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.stats.RoundStats
import kotlinx.serialization.Serializable

/*
 * REST contracts (dossier 5: "REST apenas para auth e histórico", and 8). Bodies are JSON encoded
 * with [PalavramentoJson]. Authenticated endpoints take `Authorization: Bearer <accessToken>`; the
 * WebSocket takes the same access token in `ClientMessage.JoinRoom.sessionToken`.
 *
 * POST /auth/guest     GuestAuthRequest -> AuthTokens   creates an anonymous player
 * POST /auth/refresh   RefreshRequest   -> AuthTokens   rotates the refresh token
 * POST /auth/register  RegisterRequest  -> AuthTokens   authenticated as a guest: promotes that
 *                                                       same player (same playerId, history kept)
 * POST /auth/login     LoginRequest     -> AuthTokens   if authenticated as a guest, that guest's
 *                                                       history migrates into the account
 * GET  /players/me             -> PlayerProfile
 * GET  /players/me/stats       -> LifetimeStats
 * GET  /players/me/rounds?limit=50 -> List<RoundHistoryEntry>, newest first
 * DELETE /players/me           -> 204, erases the player and everything about them (ADR 0020)
 */

@Serializable
data class GuestAuthRequest(val displayName: String? = null)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class RegisterRequest(val email: String, val password: String, val displayName: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

/** Short-lived JWT access token plus an opaque refresh token (dossier 8). */
@Serializable
data class AuthTokens(
  val playerId: String,
  val displayName: String,
  val isGuest: Boolean,
  val accessToken: String,
  val accessTokenExpiresAt: Long,
  val refreshToken: String,
)

/** Lobby header (dossier 6.1): name, level and XP progress. */
@Serializable
data class PlayerProfile(
  val playerId: String,
  val displayName: String,
  val isGuest: Boolean,
  val level: Int,
  val totalXp: Long,
  val xpForNextLevel: Long,
)

/** Lifetime statistics panel of the lobby (dossier 6.1). Averages are per completed game. */
@Serializable
data class LifetimeStats(
  val totalScore: Long,
  val totalWords: Long,
  val bestGameScore: Int,
  val bestWord: String?,
  val bestWordScore: Int,
  val gamesCompleted: Int,
  val gamesPlayed: Int,
  val averageScore: Double,
  val averageWords: Double,
  val averagePointsPerWord: Double,
  val bestRank: Int?,
)

/** One past round as the client caches it for offline review (dossier 7, client). */
@Serializable
data class RoundHistoryEntry(
  val roundId: String,
  val startsAt: Long,
  val board: List<Tile>,
  val mutator: Mutator,
  val themeTitle: String,
  val themeSubtitle: String,
  val maxScore: Int,
  val maxWords: Int,
  val stats: RoundStats,
  val rank: Int,
  val totalPlayers: Int,
  val words: List<LabelledWord>,
)
