// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.common

/**
 * Formats [remainingMs] as `MM:SS` (dossier 6.2/6.3 countdowns), clamped so a stale or negative
 * value never prints as negative time.
 */
fun formatCountdown(remainingMs: Long): String {
  val totalSeconds = (remainingMs / MillisPerSecond).coerceAtLeast(0)
  val minutes = totalSeconds / SecondsPerMinute
  val seconds = totalSeconds % SecondsPerMinute
  return "%02d:%02d".format(minutes, seconds)
}

/**
 * The four digits of [formatCountdown]'s `MM:SS`, in display order (minutes tens, minutes units,
 * seconds tens, seconds units). Used by the flip countdown (task brief 4) to animate exactly the
 * digit that changed instead of the whole string.
 */
fun countdownDigits(remainingMs: Long): List<Int> =
  formatCountdown(remainingMs).filter { it.isDigit() }.map { it - '0' }

/** True in the last [UrgencyThresholdMs] of a countdown (task brief 4: urgency color). */
fun isCountdownUrgent(remainingMs: Long): Boolean = remainingMs in 1..UrgencyThresholdMs

private const val MillisPerSecond = 1000L
private const val SecondsPerMinute = 60L
private const val UrgencyThresholdMs = 10_000L
