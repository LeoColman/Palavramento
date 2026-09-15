// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.clock

/**
 * One clock-sync round trip sample (dossier 5.3). [clientSentAtElapsedMs] and
 * [clientReceivedAtElapsedMs] live in the client's own monotonic elapsed-time domain (never the
 * wall clock, per the task brief); [serverTimeMs] is the server's wall-clock estimate echoed back
 * in `ClockSyncResponse`.
 */
data class ClockSyncSample(
  val clientSentAtElapsedMs: Long,
  val serverTimeMs: Long,
  val clientReceivedAtElapsedMs: Long,
) {
  init {
    require(clientReceivedAtElapsedMs >= clientSentAtElapsedMs) {
      "A response cannot arrive before it was sent"
    }
  }

  /** Round trip time for this sample, in milliseconds. */
  val roundTripMs: Long get() = clientReceivedAtElapsedMs - clientSentAtElapsedMs

  /** Elapsed-time midpoint of the round trip: the best guess of when [serverTimeMs] was true. */
  val midpointElapsedMs: Long get() = clientSentAtElapsedMs + roundTripMs / 2

  /** Offset to add to a future elapsed-time reading to estimate the server's wall clock. */
  val offsetMs: Long get() = serverTimeMs - midpointElapsedMs
}

/**
 * Keeps the lowest-round-trip sample out of the handful taken during the clock sync handshake
 * (task brief: "keep the lowest round-trip one"). A lower round trip means less uncertainty about
 * where the midpoint, and therefore the offset, actually falls.
 */
class ClockSyncEstimator {
  private var best: ClockSyncSample? = null

  /** Records a new [sample], keeping it only if its round trip beats the current best. */
  fun record(sample: ClockSyncSample) {
    val current = best
    if (current == null || sample.roundTripMs < current.roundTripMs) best = sample
  }

  /** The best sample recorded so far, or null if [record] was never called. */
  val bestSample: ClockSyncSample? get() = best

  /** [ClockSyncSample.offsetMs] of [bestSample], or null before the first sample. */
  val offsetMs: Long? get() = best?.offsetMs
}
