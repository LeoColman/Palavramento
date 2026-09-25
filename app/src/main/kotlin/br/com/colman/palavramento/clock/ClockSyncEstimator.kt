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
 * A sample also wins for being recent: once the kept one is older than [staleAfterMs], a sample
 * with a worse round trip still replaces it. Two reasons, both seen in the wild:
 *
 * - The first samples of a connection are the worst ones. On a mobile network the radio wakes up to
 *   send them, so the request crawls out while the answer comes back fast, and that asymmetry lands
 *   whole seconds of bias in the offset. Later samples, with the radio already awake, are honest.
 * - The server's own wall clock can step. Clinging forever to a sample taken before the step would
 *   keep the countdown pointing at a moment in time that no longer exists.
 *
 * [maxRoundTripMs] is what bounds the damage, and it is the whole point of this class. A sample's
 * offset is wrong by `(uplink - downlink) / 2`, so its error can never exceed half its own round
 * trip: a sample measured over a second is worth at most half a second of error, and one measured
 * over five seconds can be two and a half seconds wrong on its own. A player reported a round
 * ending with two seconds still on their countdown, which is exactly that: a five second sample had
 * become the clock. Ranking samples against each other cannot prevent it, because "least bad" says
 * nothing about how bad. So a sample over [maxRoundTripMs] never displaces one under it, however
 * stale the kept one has gone, and the countdown's error stays under half the ceiling.
 *
 * The exception is having no clock at all: the very first sample is kept whatever it measured,
 * since a rough countdown beats none, and anything inside the ceiling replaces it on arrival.
 */
class ClockSyncEstimator(
  private val staleAfterMs: Long = DefaultStaleAfterMs,
  private val maxRoundTripMs: Long = DefaultMaxRoundTripMs,
) {
  private var best: ClockSyncSample? = null

  /**
   * Records a new [sample], keeping it when it beats the current best, or when the best went stale
   * and this one is still inside [maxRoundTripMs]. See [winsOver] for each case.
   */
  fun record(sample: ClockSyncSample) {
    val current = best
    if (current == null) {
      best = sample
      return
    }
    if (winsOver(sample, current)) best = sample
  }

  private fun winsOver(sample: ClockSyncSample, current: ClockSyncSample): Boolean {
    val isTrusted = sample.roundTripMs <= maxRoundTripMs
    val currentIsTrusted = current.roundTripMs <= maxRoundTripMs
    val currentWentStale = sample.clientReceivedAtElapsedMs - current.clientReceivedAtElapsedMs > staleAfterMs
    return when {
      // The rule the countdown depends on: a sample this slow could be seconds wrong by itself, so
      // it never takes over from one that cannot be, no matter how old that one is.
      !isTrusted && currentIsTrusted -> false
      // Nothing inside the ceiling has arrived yet, so the kept sample is only a placeholder: any
      // trusted sample replaces it outright, and an untrusted one only if it measured less.
      !currentIsTrusted -> isTrusted || sample.roundTripMs < current.roundTripMs
      // Both are inside the ceiling, where the original rules apply and either is safe to trust.
      else -> sample.roundTripMs < current.roundTripMs || currentWentStale
    }
  }

  /** The best sample recorded so far, or null if [record] was never called. */
  val bestSample: ClockSyncSample? get() = best

  /** [ClockSyncSample.offsetMs] of [bestSample], or null before the first sample. */
  val offsetMs: Long? get() = best?.offsetMs

  private companion object {
    /** 45 s: long enough for a round to keep one good sample, short enough to follow a server step. */
    const val DefaultStaleAfterMs = 45_000L

    /**
     * 1 s, which caps the countdown's error at 500 ms. That is the server's own late-submission
     * tolerance, the margin by which `RoundEnd` already trails `endsAt`, so a countdown this close
     * reaches 00:00 before the results screen arrives rather than being cut off with time on it.
     * A healthy WebSocket round trip is a tenth of this, so the ceiling only ever rejects samples
     * taken while the network was in trouble, which are the ones worth rejecting.
     */
    const val DefaultMaxRoundTripMs = 1_000L
  }
}
