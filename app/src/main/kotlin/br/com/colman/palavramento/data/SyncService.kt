// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.data

import br.com.colman.palavramento.network.RestApi

/**
 * Refreshes the local cache from the server (dossier 7, task brief 2): after each `RoundEnd` and
 * when the lobby opens, fetch `/players/me/rounds`, `/players/me` and (for a registered player)
 * `/players/me/stats`, then upsert everything into [profileRepository]/[historyRepository]. The
 * server is authoritative and the cache is what the UI actually reads, so a network failure here
 * (checked one call at a time, never all-or-nothing) simply leaves the cache exactly as it was -
 * the acceptance criterion "Historico visivel offline" - instead of clearing it or crashing.
 */
class SyncService(
  private val restApi: RestApi,
  private val authController: AuthController,
  private val profileRepository: ProfileRepository,
  private val historyRepository: HistoryRepository,
) {

  /** True when at least one of the three fetches succeeded and was written to the cache. */
  suspend fun sync(): Boolean {
    val profileSynced = authController.callAuthenticated { token -> restApi.playerProfile(token) }
      ?.also { profileRepository.saveProfile(it) }

    // Guests have no lifetime stats (ADR 0007, /players/me/stats answers 403): only fetched for a
    // registered player, so a guest never even attempts a call the server is guaranteed to reject.
    if (profileSynced?.isGuest == false) {
      authController.callAuthenticated { token -> restApi.lifetimeStats(token) }
        ?.also { profileRepository.saveStats(it) }
    }

    val roundsSynced = authController.callAuthenticated { token -> restApi.roundHistory(token) }
      ?.also { historyRepository.upsertAll(it) }

    return profileSynced != null || roundsSynced != null
  }
}
