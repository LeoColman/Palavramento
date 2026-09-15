// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.network

import android.util.Log
import br.com.colman.palavramento.clock.ClockSyncEstimator
import br.com.colman.palavramento.clock.ClockSyncSample
import br.com.colman.palavramento.clock.ServerClock
import br.com.colman.palavramento.domain.protocol.ClientMessage
import br.com.colman.palavramento.domain.protocol.ServerMessage
import br.com.colman.palavramento.state.MatchStateReducer
import br.com.colman.palavramento.state.MatchUiState
import br.com.colman.palavramento.state.OptimisticSubmission
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
 * Unstable-network handling (task brief 5, `docs/adr/0008-polimento-do-app.md`): [state] keeps the
 * last room screen it reached (Lobby/InRound/PostRound) across a drop instead of resetting to
 * [MatchUiState.Disconnected] - so a mid-round reconnect never makes the found-words list disappear
 * from view even for the moment the socket is down - while [connectionStatus] is the separate signal
 * the UI uses to show a "Reconectando..." banner over that same screen.
 * [submitWord] queues its message in [pendingSubmissions] instead of sending to a dead transport,
 * and [collectUntilDisconnected] flushes it once a fresh `RoundStart` confirms which round is
 * actually running after reconnecting.
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

  private val connectionStatusFlow = MutableStateFlow(ConnectionStatus.Reconnecting)
  val connectionStatus: StateFlow<ConnectionStatus> = connectionStatusFlow

  private val clockFlow = MutableStateFlow<ServerClock?>(null)
  val clock: StateFlow<ServerClock?> = clockFlow

  // Orchestrator finding (task brief 4): consecutive failed connection attempts since the last
  // success, so the UI can tell "still trying the very first connection" apart from "connected once,
  // now reconnecting" and show an error+retry state instead of spinning forever (see RoomScreen).
  private val connectionAttemptsFlow = MutableStateFlow(0)
  val connectionAttempts: StateFlow<Int> = connectionAttemptsFlow

  private val pendingSubmissions = PendingSubmissionQueue()

  @Volatile
  private var running = false

  /**
   * Runs the connect/handshake/receive loop until [stop] or [disconnect] is called, reconnecting
   * forever on drop. Meant to be (re)launched in a coroutine scope tied to the room screen's
   * lifecycle - a fresh launch after [disconnect] reconnects exactly like any other drop.
   */
  suspend fun run() {
    running = true
    connectionStatusFlow.value = ConnectionStatus.Reconnecting
    var attempt = 0
    while (running) {
      val connected = tryConnectAndHandshake()
      if (connected) {
        attempt = 0
        connectionAttemptsFlow.value = 0
        connectionStatusFlow.value = ConnectionStatus.Connected
        collectUntilDisconnected()
      } else {
        attempt++
        connectionAttemptsFlow.value = attempt
      }
      if (!running) break
      connectionStatusFlow.value = ConnectionStatus.Reconnecting
      // Orchestrator finding (task brief 4): keep the last synced clock across a drop instead of
      // nulling it out here - the match countdown otherwise reads null and renders 00:00 for the
      // whole "Reconectando..." window. tryConnectAndHandshake only overwrites it once a fresh
      // handshake actually produced a new offset.
      delay(backoff.delayForAttempt(attempt))
    }
  }

  /** Stops [run]'s loop after the current connection attempt settles, without closing the socket. */
  fun stop() {
    running = false
  }

  /**
   * Stops [run]'s loop and closes the current connection, if any (task brief 5: disconnect when the
   * app is backgrounded). Safe to call even when not connected. [run] can be relaunched afterwards
   * to reconnect from scratch, replaying the same handshake/`JoinRoom` path as any other connection.
   */
  suspend fun disconnect() {
    running = false
    transport.close()
  }

  /**
   * Runs the optimistic local verdict (ADR 0014, [OptimisticSubmission]) for [path] against the
   * current [state] before doing anything network-related: an [OptimisticSubmission.Decision.Accept]
   * or [OptimisticSubmission.Decision.Reject] applies its `newState` to [state] immediately (found
   * word/score/feedback, or just the rejection feedback), and only `Accept` still reaches
   * [sendSubmission] below; `Reject` never does, since the same validator running on the server would
   * reject it too. [OptimisticSubmission.Decision.Defer] (no [MatchUiState.InRound.validWords] yet,
   * or the round is already over) - and a submission for a round [state] does not currently show -
   * fall straight through to [sendSubmission], exactly as before this feature.
   */
  suspend fun submitWord(roundId: String, path: List<Int>, clientTimestampMs: Long) {
    val current = stateFlow.value
    if (current is MatchUiState.InRound && current.roundId == roundId) {
      when (val decision = OptimisticSubmission.decide(current, path, clockFlow.value?.nowMs())) {
        is OptimisticSubmission.Decision.Accept -> {
          stateFlow.value = decision.newState
          sendSubmission(roundId, path, clientTimestampMs)
          return
        }

        is OptimisticSubmission.Decision.Reject -> {
          stateFlow.value = decision.newState
          return
        }

        OptimisticSubmission.Decision.Defer -> Unit
      }
    }
    sendSubmission(roundId, path, clientTimestampMs)
  }

  /**
   * Sends [ClientMessage.SubmitWord] when connected; while disconnected, queues it in
   * [pendingSubmissions] instead (task brief 5), to be resent once a reconnect confirms the same
   * round is still running. A send that fails despite [connectionStatus] reading `Connected` (the
   * drop has not been detected yet) is queued the same way rather than silently lost.
   */
  private suspend fun sendSubmission(roundId: String, path: List<Int>, clientTimestampMs: Long) {
    val message = ClientMessage.SubmitWord(roundId, path, clientTimestampMs)
    if (connectionStatusFlow.value != ConnectionStatus.Connected) {
      pendingSubmissions.enqueue(message)
      return
    }
    runCatching { transport.send(message) }.onFailure { pendingSubmissions.enqueue(message) }
  }

  suspend fun leaveRoom() {
    pendingSubmissions.clear()
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
    // Only overwrite the clock once a fresh offset is actually available: leaving the previous
    // ServerClock in place otherwise (instead of a stray null) is what keeps the match countdown
    // ticking through a "Reconectando..." window (task brief 4).
    estimator.offsetMs?.let { offset -> clockFlow.value = ServerClock(offset, elapsedRealtimeMs) }
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
        when (message) {
          is ServerMessage.ClockSyncResponse -> Unit
          is ServerMessage.RoundStart -> {
            stateFlow.value = MatchStateReducer.reduce(stateFlow.value, message)
            flushPendingSubmissions(message.roundId)
          }
          is ServerMessage.WordRejected -> {
            logIfRollingBackAnOptimisticAccept(message)
            stateFlow.value = MatchStateReducer.reduce(stateFlow.value, message)
          }
          else -> stateFlow.value = MatchStateReducer.reduce(stateFlow.value, message)
        }
      }
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (failure: Exception) {
      // Falls through to run()'s reconnect path, same as a normal channel close.
    }
  }

  /**
   * ADR 0014: a `WordRejected` for a path this client already accepted locally means the server
   * disagreed with the optimistic verdict - rare, the equivalence property test in `:domain` is the
   * argument it should not happen - so it is worth a warning before [MatchStateReducer] rolls it
   * back. Checked here, not inside the reducer, so the reducer itself stays free of Android/logging.
   */
  private fun logIfRollingBackAnOptimisticAccept(message: ServerMessage.WordRejected) {
    val current = stateFlow.value
    if (current is MatchUiState.InRound && message.path in current.pendingPaths) {
      Log.w(Tag, "Server rejected a word accepted locally (${message.reason}), rolling back: ${message.path}")
    }
  }

  /**
   * Sends every submission queued for [currentRoundId] (task brief 5), dropping anything queued for
   * a round that is no longer the one running. A resend the server already accepted before the drop
   * comes back as `WordRejected(JA_ENCONTRADA)` (dossier 5.1), which
   * [MatchStateReducer.reduce][br.com.colman.palavramento.state.MatchStateReducer] already treats as
   * a harmless rejection that never touches the found-words list.
   */
  private suspend fun flushPendingSubmissions(currentRoundId: String) {
    pendingSubmissions.drain(currentRoundId).forEach { submission -> transport.send(submission) }
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
    const val Tag = "MultiplayerSession"
  }
}
