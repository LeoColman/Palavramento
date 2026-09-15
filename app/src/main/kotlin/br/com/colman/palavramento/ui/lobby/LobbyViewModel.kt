// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.palavramento.data.TokenRepository
import br.com.colman.palavramento.domain.protocol.LifetimeStats
import br.com.colman.palavramento.domain.protocol.PlayerProfile
import br.com.colman.palavramento.network.RestApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Bootstraps a guest identity on first launch (`POST /auth/guest`, dossier 8) and loads the lobby
 * header/stats (dossier 6.1). Login/promotion is phase 5 (task brief); [onLoginClicked] is a stub
 * on purpose.
 */
class LobbyViewModel(private val restApi: RestApi, private val tokenRepository: TokenRepository) : ViewModel() {

  private val mutableUiState = MutableStateFlow(LobbyUiState())
  val uiState: StateFlow<LobbyUiState> = mutableUiState

  init {
    viewModelScope.launch { bootstrap() }
  }

  private suspend fun bootstrap() {
    val existing = tokenRepository.tokens.first()
    // A failed guestAuth() (no connectivity on first launch) must not crash viewModelScope: fall
    // back to an empty, still-guest lobby instead of an uncaught exception.
    val tokens = existing ?: runCatching { restApi.guestAuth() }.getOrNull()?.also { tokenRepository.save(it) }
    if (tokens == null) {
      mutableUiState.value = mutableUiState.value.copy(isLoading = false)
      return
    }

    mutableUiState.value = mutableUiState.value.copy(isGuest = tokens.isGuest, isLoading = true)
    val profile = runCatching { restApi.playerProfile(tokens.accessToken) }.getOrNull()
    val stats = runCatching { restApi.lifetimeStats(tokens.accessToken) }.getOrNull()
    mutableUiState.value = mutableUiState.value.copy(profile = profile, stats = stats, isLoading = false)
  }

  /** Stub: guest promotion / email login is phase 5 (task brief). Intentionally a no-op for now. */
  fun onLoginClicked() = Unit
}

data class LobbyUiState(
  val isLoading: Boolean = true,
  val isGuest: Boolean = true,
  val profile: PlayerProfile? = null,
  val stats: LifetimeStats? = null,
)
