// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.audio

/**
 * How fast the background music plays, as a pure function of the milliseconds left in the round
 * (task brief: "define the speed as a pure function of the remaining time"). Chosen to line up with
 * the countdown's own urgency cue
 * ([isCountdownUrgent][br.com.colman.palavramento.ui.common.isCountdownUrgent], last 10s, digits turn
 * red):
 *
 * - More than [RampStartMs] (30s) left: [BaseSpeed] (1.0x), normal tempo.
 * - Between [RampStartMs] and [UrgentThresholdMs] (10s) left: a linear ramp from [BaseSpeed] up to
 *   [RampSpeed] (1.25x), so the track is audibly quickening well before the countdown turns red.
 * - [UrgentThresholdMs] or less left: [PeakSpeed] (1.5x) flat, a deliberate jump up from the ramp
 *   (not its smooth continuation) timed with the countdown's own red digits, for a "hurry up" moment
 *   that reads as a distinct event rather than just the tail of a gradual change.
 *
 * Always within `[BaseSpeed, PeakSpeed]` and never lower for a smaller [remainingMs] (monotonic,
 * checked as a property in `MusicSpeedCurveTest`).
 */
object MusicSpeedCurve {

  const val BaseSpeed = 1.0f
  const val RampSpeed = 1.25f
  const val PeakSpeed = 1.5f

  const val RampStartMs = 30_000L
  const val UrgentThresholdMs = 10_000L

  /** The music's playback speed multiplier for [remainingMs] milliseconds left in the round. */
  fun speedFor(remainingMs: Long): Float {
    val remaining = remainingMs.coerceAtLeast(0L)
    return when {
      remaining <= UrgentThresholdMs -> PeakSpeed
      remaining >= RampStartMs -> BaseSpeed
      else -> {
        val rampSpan = (RampStartMs - UrgentThresholdMs).toFloat()
        val rampProgress = (RampStartMs - remaining) / rampSpan
        BaseSpeed + (RampSpeed - BaseSpeed) * rampProgress
      }
    }
  }
}
