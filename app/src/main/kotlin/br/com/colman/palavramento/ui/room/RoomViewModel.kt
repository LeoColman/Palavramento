// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.palavramento.clock.ServerClock
import br.com.colman.palavramento.network.MultiplayerSession
import br.com.colman.palavramento.state.MatchUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the room's [MultiplayerSession] for as long as this screen is on the back stack: starts the
 * connect/handshake/reconnect loop when created, exposes its [state] and [clock], and leaves the
 * room (and stops reconnecting) when the player navigates back.
 */
class RoomViewModel(private val session: MultiplayerSession) : ViewModel() {

  val state: StateFlow<MatchUiState> = session.state
  val clock: StateFlow<ServerClock?> = session.clock

  init {
    viewModelScope.launch { session.run() }
  }

  fun submitWord(roundId: String, path: List<Int>, clientTimestampMs: Long) {
    viewModelScope.launch { runCatching { session.submitWord(roundId, path, clientTimestampMs) } }
  }

  fun leaveRoom() {
    session.stop()
    viewModelScope.launch { runCatching { session.leaveRoom() } }
  }

  override fun onCleared() {
    session.stop()
  }
}
