// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.network

import br.com.colman.palavramento.clock.ClockSyncEstimator
import br.com.colman.palavramento.clock.ClockSyncSample
import br.com.colman.palavramento.clock.ServerClock
import br.com.colman.palavramento.domain.protocol.ClientMessage
import br.com.colman.palavramento.domain.protocol.ServerMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * How the clock sync behaves. Bundled instead of loose parameters so a test can slow the re-sync
 * off (`resyncIntervalMs = 0`) or drive [wait] by hand, and so
 * [MultiplayerSession]'s own parameter list does not grow for it.
 *
 * [wait] is separate from the session's reconnect delay on purpose: a test that makes the reconnect
 * backoff instant would otherwise turn this loop into a spin.
 */
data class ClockSyncSettings(
  val sampleCount: Int = DefaultSampleCount,
  /** 20 s: several fresh samples inside a two minute round, one tiny frame each. */
  val resyncIntervalMs: Long = 20_000L,
  val wait: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
  companion object {
    const val DefaultSampleCount = 3
  }
}

/**
 * Keeps [clockFlow] pointing at the server's clock for as long as a connection lives (dossier 5.3).
 *
 * [handshake] takes the first samples, before `JoinRoom`, so the countdown has an offset the moment
 * a round arrives. [keepSyncing] then keeps asking while the connection is up, and [record] folds
 * every answer into the same [ClockSyncEstimator].
 *
 * Re-syncing matters because the samples taken at connect time are the worst ones available: on a
 * mobile network the radio wakes up to carry them, so the request crawls out while the answer comes
 * back fast, and that asymmetry lands whole seconds of bias in the offset. A round lasts two
 * minutes, so the player then watches a countdown that disagrees with the server about when the
 * round ends, and the results screen shows up while their own clock still reads seconds left. The
 * same re-syncing absorbs a step of the server's own wall clock, which a single sample at connect
 * time would carry to the end of the round.
 */
internal class ClockSyncCoordinator(
  private val transport: MultiplayerTransport,
  private val elapsedRealtimeMs: () -> Long,
  private val settings: ClockSyncSettings,
  private val clockFlow: MutableStateFlow<ServerClock?>,
) {

  private var estimator: ClockSyncEstimator? = null

  /**
   * Round-trips [sampleCount] `ClockSync` messages and publishes the offset, replacing whatever a
   * previous connection had measured. Reads straight off the transport, which nothing else is
   * collecting yet: this runs before `JoinRoom`, so no other message can be on the wire.
   */
  suspend fun handshake() {
    val fresh = ClockSyncEstimator()
    estimator = fresh
    repeat(settings.sampleCount) {
      val sentAt = elapsedRealtimeMs()
      transport.send(ClientMessage.ClockSync(sentAt))
      val response = transport.incoming()
        .first { it is ServerMessage.ClockSyncResponse && it.clientSentAt == sentAt }
        as ServerMessage.ClockSyncResponse
      fresh.record(ClockSyncSample(sentAt, response.serverTime, elapsedRealtimeMs()))
    }
    publish()
  }

  /** Asks for the time every [ClockSyncSettings.resyncIntervalMs] until cancelled. Answers reach [record]. */
  suspend fun keepSyncing() {
    // Zero turns it off, which is how a test keeps a pending timer out of its virtual clock.
    if (settings.resyncIntervalMs <= 0) return
    while (true) {
      settings.wait(settings.resyncIntervalMs)
      runCatching { transport.send(ClientMessage.ClockSync(elapsedRealtimeMs())) }
    }
  }

  /** Folds one answer into this connection's estimator and republishes the clock. */
  fun record(response: ServerMessage.ClockSyncResponse) {
    val current = estimator ?: return
    val receivedAt = elapsedRealtimeMs()
    // elapsedRealtime only moves forward, so this only guards against an answer to a ClockSync sent
    // by an older connection, whose sentAt belongs to a different estimator's timeline.
    if (receivedAt < response.clientSentAt) return
    current.record(ClockSyncSample(response.clientSentAt, response.serverTime, receivedAt))
    publish()
  }

  private fun publish() {
    estimator?.offsetMs?.let { offset -> clockFlow.value = ServerClock(offset, elapsedRealtimeMs) }
  }
}
