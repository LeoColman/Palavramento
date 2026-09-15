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

private const val MillisPerSecond = 1000L
private const val SecondsPerMinute = 60L
