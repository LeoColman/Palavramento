// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.network

import br.com.colman.palavramento.domain.protocol.AuthTokens
import br.com.colman.palavramento.domain.protocol.GuestAuthRequest
import br.com.colman.palavramento.domain.protocol.LifetimeStats
import br.com.colman.palavramento.domain.protocol.PlayerProfile
import br.com.colman.palavramento.domain.protocol.RefreshRequest

/**
 * The REST surface the app needs for phase 4 (task brief: `POST /auth/guest` on first launch, plus
 * the lobby profile/stats reads). `RegisterRequest`/`LoginRequest` (`Rest.kt`) are phase 5 (guest
 * promotion and login) and deliberately have no method here yet, matching "keep the repository
 * interface easy to extend" for the surrounding [br.com.colman.palavramento.data.TokenRepository].
 */
interface RestApi {
  suspend fun guestAuth(request: GuestAuthRequest = GuestAuthRequest()): AuthTokens
  suspend fun refresh(request: RefreshRequest): AuthTokens
  suspend fun playerProfile(accessToken: String): PlayerProfile
  suspend fun lifetimeStats(accessToken: String): LifetimeStats
}
