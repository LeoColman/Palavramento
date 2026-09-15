// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.palavramento.clock.ServerClock
import br.com.colman.palavramento.network.ConnectionStatus
import br.com.colman.palavramento.network.MultiplayerSession
import br.com.colman.palavramento.state.MatchUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the room's [MultiplayerSession] for as long as this screen is on the back stack: starts the
 * connect/handshake/reconnect loop when created, exposes its [state], [clock] and
 * [connectionStatus], and leaves the room (and stops reconnecting) when the player navigates back.
 *
 * [pause]/[resume] let [RoomScreen] additionally disconnect/reconnect on app background/foreground
 * (task brief 5) without leaking coroutines: [pause] cancels the loop job it started instead of
 * relying only on [MultiplayerSession.stop], which would leave a `collect` suspended on an open
 * socket until the next natural drop.
 */
class RoomViewModel(private val session: MultiplayerSession) : ViewModel() {

  val state: StateFlow<MatchUiState> = session.state
  val clock: StateFlow<ServerClock?> = session.clock
  val connectionStatus: StateFlow<ConnectionStatus> = session.connectionStatus

  private var runJob: Job? = null

  init {
    start()
  }

  /** (Re)starts the connect/handshake/reconnect loop; a no-op while it is already running. */
  fun start() {
    if (runJob?.isActive == true) return
    runJob = viewModelScope.launch { session.run() }
  }

  /** Alias for [start], for the app-foreground lifecycle event. */
  fun resume() = start()

  /** Cancels the loop and closes the current connection (task brief 5: app background). */
  fun pause() {
    runJob?.cancel()
    viewModelScope.launch { session.disconnect() }
  }

  fun submitWord(roundId: String, path: List<Int>, clientTimestampMs: Long) {
    viewModelScope.launch { runCatching { session.submitWord(roundId, path, clientTimestampMs) } }
  }

  fun leaveRoom() {
    session.stop()
    runJob?.cancel()
    viewModelScope.launch {
      runCatching { session.leaveRoom() }
      session.disconnect()
    }
  }

  override fun onCleared() {
    session.stop()
    runJob?.cancel()
  }
}
