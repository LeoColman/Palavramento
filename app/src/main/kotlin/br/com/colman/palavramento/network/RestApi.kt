// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.network

import br.com.colman.palavramento.domain.protocol.AuthTokens
import br.com.colman.palavramento.domain.protocol.GuestAuthRequest
import br.com.colman.palavramento.domain.protocol.LifetimeStats
import br.com.colman.palavramento.domain.protocol.LoginRequest
import br.com.colman.palavramento.domain.protocol.PlayerProfile
import br.com.colman.palavramento.domain.protocol.RefreshRequest
import br.com.colman.palavramento.domain.protocol.RegisterRequest
import br.com.colman.palavramento.domain.protocol.RoundHistoryEntry

/** `/players/me/rounds` default (dossier 7, `Rest.kt`), mirroring the server's own default. */
const val DefaultRoundHistoryLimit = 50

/**
 * The REST surface the app needs (`Rest.kt`): guest bootstrap, refresh, guest promotion
 * (`register`)/login-migration (`login`), and the lobby/history reads. Every method either returns
 * the successful body or throws - a non-2xx response surfaces as Ktor's own
 * `io.ktor.client.plugins.ResponseException` (`ClientRequestException` for 4xx,
 * `ServerResponseException` for 5xx; the client is `expectSuccess = true` by default), which callers
 * that need to tell "rejected" apart from "unreachable" inspect via `.response.status` (see
 * [br.com.colman.palavramento.data.AuthController]) instead of this interface growing a parallel
 * result type for every call.
 */
interface RestApi {
  suspend fun guestAuth(request: GuestAuthRequest = GuestAuthRequest()): AuthTokens
  suspend fun refresh(request: RefreshRequest): AuthTokens

  /**
   * [guestAccessToken] is the currently signed-in guest's access token, sent so the server promotes
   * that same player (dossier 8) instead of creating an unrelated account; null for a register with
   * no guest session at all.
   */
  suspend fun register(request: RegisterRequest, guestAccessToken: String?): AuthTokens

  /** [guestAccessToken] present migrates that guest's history into the account being logged into. */
  suspend fun login(request: LoginRequest, guestAccessToken: String?): AuthTokens

  suspend fun playerProfile(accessToken: String): PlayerProfile
  suspend fun lifetimeStats(accessToken: String): LifetimeStats
  suspend fun roundHistory(accessToken: String, limit: Int = DefaultRoundHistoryLimit): List<RoundHistoryEntry>
}
