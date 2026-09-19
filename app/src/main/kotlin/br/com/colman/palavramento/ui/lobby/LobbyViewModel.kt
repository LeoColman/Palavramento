// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.lobby

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.palavramento.data.AuthController
import br.com.colman.palavramento.data.ProfileRepository
import br.com.colman.palavramento.data.SyncService
import br.com.colman.palavramento.domain.protocol.LifetimeStats
import br.com.colman.palavramento.domain.protocol.PlayerProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Bootstraps a guest identity on first launch (`POST /auth/guest`, dossier 8), then syncs the local
 * cache (dossier 7, task brief 2) and exposes it for the lobby header/stats panel (dossier 6.1). The
 * cache, not the last network response, is what [uiState] shows: [profileRepository] keeps emitting
 * whatever it last had even when a [refresh] fails, which is what makes the lobby (and, through the
 * same cache, the history screen) work with the network off.
 */
class LobbyViewModel(
  private val authController: AuthController,
  private val syncService: SyncService,
  profileRepository: ProfileRepository,
) : ViewModel() {

  private val mutableUiState = MutableStateFlow(LobbyUiState())
  val uiState: StateFlow<LobbyUiState> = mutableUiState

  init {
    viewModelScope.launch {
      profileRepository.profile().collect { p ->
        mutableUiState.update {
          it.copy(
            profile = p
          )
        }
      }
    }
    viewModelScope.launch { profileRepository.stats().collect { s -> mutableUiState.update { it.copy(stats = s) } } }
    // Follows the stored session rather than what [refresh] bootstrapped: a rejected refresh token
    // can swap a registered player for a new guest in the middle of a sync.
    viewModelScope.launch {
      authController.session.collect { tokens -> mutableUiState.update { it.copy(isGuest = tokens?.isGuest ?: true) } }
    }
    viewModelScope.launch {
      authController.sessionExpired.collect { expired -> mutableUiState.update { it.copy(sessionExpired = expired) } }
    }
    refresh()
  }

  /**
   * (Re)runs guest bootstrap + cache sync (task brief 2: "after each RoundEnd and when the lobby
   * opens"; [br.com.colman.palavramento.ui.lobby.LobbyScreen] also calls this on `ON_RESUME`, which
   * covers returning from Login/History). A failure never clears [uiState]'s already-cached
   * profile/stats - it only raises [LobbyUiState.loadError] so the screen can offer a retry, per the
   * orchestrator's end-to-end finding that a silent failure here left "Jogar" spinning forever with
   * no explanation.
   */
  fun refresh() {
    viewModelScope.launch {
      mutableUiState.update { it.copy(isLoading = true, loadError = false) }
      val tokens = authController.bootstrap()
      if (tokens == null) {
        Log.w(Tag, "Guest bootstrap failed: no connectivity or the server is unreachable")
        mutableUiState.update { it.copy(isLoading = false, loadError = true) }
        return@launch
      }
      val synced = syncService.sync()
      if (!synced) Log.w(Tag, "Lobby cache sync failed; showing the last cached data, if any")
      mutableUiState.update { it.copy(isLoading = false, loadError = !synced) }
    }
  }

  /** Task brief 4: "Logout returns to a fresh guest". */
  fun onLogoutClicked() {
    viewModelScope.launch {
      authController.logout()
      refresh()
    }
  }

  /** The player whose session expired chose to keep playing as a guest for now. */
  fun onSessionExpiredDismissed() {
    viewModelScope.launch { authController.dismissSessionExpired() }
  }

  private companion object {
    const val Tag = "LobbyViewModel"
  }
}

data class LobbyUiState(
  val isLoading: Boolean = true,
  val isGuest: Boolean = true,
  val profile: PlayerProfile? = null,
  val stats: LifetimeStats? = null,
  val loadError: Boolean = false,
  val sessionExpired: Boolean = false,
)
