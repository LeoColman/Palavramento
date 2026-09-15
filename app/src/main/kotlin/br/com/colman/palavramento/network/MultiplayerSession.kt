// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.network

import br.com.colman.palavramento.clock.ClockSyncEstimator
import br.com.colman.palavramento.clock.ClockSyncSample
import br.com.colman.palavramento.clock.ServerClock
import br.com.colman.palavramento.domain.protocol.ClientMessage
import br.com.colman.palavramento.domain.protocol.ServerMessage
import br.com.colman.palavramento.state.MatchStateReducer
import br.com.colman.palavramento.state.MatchUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Owns one room membership over [transport] (dossier 5): connects, runs the clock-sync handshake,
 * joins the room, folds every other message into [state] through [MatchStateReducer], and
 * reconnects with [backoff] on drop by replaying the exact same handshake and `JoinRoom` -
 * a reconnecting client and one joining for the first time run through identical code (dossier 5.3).
 *
 * [accessTokenProvider] and [elapsedRealtimeMs] are injected functions rather than concrete
 * Android/Koin types, which is what keeps this class unit-testable on the JVM against a fake
 * [MultiplayerTransport] (see `docs/adr/0006-arquitetura-do-app.md`).
 */
class MultiplayerSession(
  private val transport: MultiplayerTransport,
  private val accessTokenProvider: suspend () -> String?,
  private val elapsedRealtimeMs: () -> Long,
  private val backoff: ReconnectBackoff = ReconnectBackoff(),
  private val delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
  private val clockSyncSampleCount: Int = DefaultClockSyncSamples,
) {
  private val stateFlow = MutableStateFlow<MatchUiState>(MatchUiState.Disconnected)
  val state: StateFlow<MatchUiState> = stateFlow

  private val clockFlow = MutableStateFlow<ServerClock?>(null)
  val clock: StateFlow<ServerClock?> = clockFlow

  @Volatile
  private var running = false

  /**
   * Runs the connect/handshake/receive loop until [stop] is called, reconnecting forever on drop.
   * Meant to be launched once in a long-lived coroutine scope (a ViewModel's, in production).
   */
  suspend fun run() {
    running = true
    var attempt = 0
    while (running) {
      val connected = tryConnectAndHandshake()
      if (connected) {
        attempt = 0
        collectUntilDisconnected()
      } else {
        attempt++
      }
      if (!running) break
      stateFlow.value = MatchUiState.Disconnected
      clockFlow.value = null
      delay(backoff.delayForAttempt(attempt))
    }
  }

  /** Stops [run]'s loop after the current connection attempt settles. */
  fun stop() {
    running = false
  }

  suspend fun submitWord(roundId: String, path: List<Int>, clientTimestampMs: Long) {
    transport.send(ClientMessage.SubmitWord(roundId, path, clientTimestampMs))
  }

  suspend fun leaveRoom() {
    transport.send(ClientMessage.LeaveRoom)
  }

  // The transport is a boundary to a real network connection: any failure while connecting or
  // handshaking (timeout, DNS, a peer that never answers ClockSync) must turn into "retry later",
  // never a crash. There is nothing more specific to react to than "the attempt failed", and no
  // logger wired into this pure class to report it to; run()'s backoff loop is the recovery path.
  @Suppress("TooGenericExceptionCaught", "SwallowedException")
  private suspend fun tryConnectAndHandshake(): Boolean = try {
    transport.connect()
    val estimator = ClockSyncEstimator()
    runClockSyncHandshake(estimator)
    val offset = estimator.offsetMs
    clockFlow.value = offset?.let { ServerClock(it, elapsedRealtimeMs) }
    transport.send(ClientMessage.JoinRoom(sessionToken = accessTokenProvider()))
    true
  } catch (cancellation: CancellationException) {
    throw cancellation
  } catch (failure: Exception) {
    false
  }

  // Same reasoning as tryConnectAndHandshake: a broken connection while collecting is expected
  // (the peer dropped, the network changed) and simply falls through to run()'s reconnect path.
  @Suppress("TooGenericExceptionCaught", "SwallowedException")
  private suspend fun collectUntilDisconnected() {
    try {
      transport.incoming().collect { message ->
        if (message !is ServerMessage.ClockSyncResponse) {
          stateFlow.value = MatchStateReducer.reduce(stateFlow.value, message)
        }
      }
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (failure: Exception) {
      // Falls through to run()'s reconnect path, same as a normal channel close.
    }
  }

  /**
   * Sends [clockSyncSampleCount] `ClockSync` samples and keeps the lowest-round-trip one (task
   * brief). Messages other than the matching `ClockSyncResponse` are not expected on the wire
   * before `JoinRoom` is sent, so they are not specially handled here.
   */
  private suspend fun runClockSyncHandshake(estimator: ClockSyncEstimator) {
    repeat(clockSyncSampleCount) {
      val sentAt = elapsedRealtimeMs()
      transport.send(ClientMessage.ClockSync(sentAt))
      val response = transport.incoming()
        .first { it is ServerMessage.ClockSyncResponse && it.clientSentAt == sentAt }
        as ServerMessage.ClockSyncResponse
      val receivedAt = elapsedRealtimeMs()
      estimator.record(ClockSyncSample(sentAt, response.serverTime, receivedAt))
    }
  }

  private companion object {
    const val DefaultClockSyncSamples = 3
  }
}
