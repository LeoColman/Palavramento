// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.round

import java.time.Instant
import kotlin.time.Duration

/**
 * Pure round-window arithmetic (dossier §1.4): [RoomScheduler] delegates every timestamp
 * computation here so the scheduling math can be unit tested with a fake clock, without spinning up
 * the scheduler's own real-time loop (which only [SystemGameClock] ever drives).
 */
object RoundTiming {
  /** When the very first round of a room starts: one intermission after [now]. */
  fun firstRoundStartsAt(now: Instant, intermissionDuration: Duration): Instant =
    now.plusMillis(intermissionDuration.inWholeMilliseconds)

  /** A round starting at [startsAt] ends [roundDuration] later. */
  fun endsAt(startsAt: Instant, roundDuration: Duration): Instant =
    startsAt.plusMillis(roundDuration.inWholeMilliseconds)

  /** The next round starts one intermission after the previous one's [previousEndsAt]. */
  fun nextRoundStartsAt(previousEndsAt: Instant, intermissionDuration: Duration): Instant =
    previousEndsAt.plusMillis(intermissionDuration.inWholeMilliseconds)

  /** The latest instant a submission for a round ending at [endsAt] is still accepted (dossier §5.2). */
  fun lateSubmissionDeadline(endsAt: Instant, tolerance: Duration): Instant =
    endsAt.plusMillis(tolerance.inWholeMilliseconds)

  /**
   * Whether a not-yet-participant joining right at [now] still gets at least [minRemaining] of a
   * round ending at [endsAt] (ADR 0010: late join). Exactly [minRemaining] left counts as allowed.
   */
  fun canLateJoin(now: Instant, endsAt: Instant, minRemaining: Duration): Boolean {
    val remainingMillis = java.time.Duration.between(now, endsAt).toMillis()
    return remainingMillis >= minRemaining.inWholeMilliseconds
  }
}
