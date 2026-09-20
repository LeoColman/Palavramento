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
 * Keeps the lowest-round-trip sample seen so far (task brief: "keep the lowest round-trip one"). A
 * lower round trip means less uncertainty about where the midpoint, and therefore the offset,
 * actually falls.
 *
 * A sample also wins for being recent: once the kept one is older than [staleAfterMs], the next
 * sample replaces it whatever its round trip. Two reasons, both seen in the wild:
 *
 * - The first samples of a connection are the worst ones. On a mobile network the radio wakes up to
 *   send them, so the request crawls out while the answer comes back fast, and that asymmetry lands
 *   whole seconds of bias in the offset. Later samples, with the radio already awake, are honest.
 * - The server's own wall clock can step. Clinging forever to a sample taken before the step would
 *   keep the countdown pointing at a moment in time that no longer exists.
 */
class ClockSyncEstimator(private val staleAfterMs: Long = DefaultStaleAfterMs) {
  private var best: ClockSyncSample? = null

  /** Records a new [sample], keeping it when it beats the current best or the best went stale. */
  fun record(sample: ClockSyncSample) {
    val current = best
    if (current == null) {
      best = sample
      return
    }
    val beatsIt = sample.roundTripMs < current.roundTripMs
    val currentWentStale = sample.clientReceivedAtElapsedMs - current.clientReceivedAtElapsedMs > staleAfterMs
    if (beatsIt || currentWentStale) best = sample
  }

  /** The best sample recorded so far, or null if [record] was never called. */
  val bestSample: ClockSyncSample? get() = best

  /** [ClockSyncSample.offsetMs] of [bestSample], or null before the first sample. */
  val offsetMs: Long? get() = best?.offsetMs

  private companion object {
    /** 45 s: long enough for a round to keep one good sample, short enough to follow a server step. */
    const val DefaultStaleAfterMs = 45_000L
  }
}
