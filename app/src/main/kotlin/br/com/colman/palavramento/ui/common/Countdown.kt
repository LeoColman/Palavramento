// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import br.com.colman.palavramento.clock.ServerClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val TickIntervalMs = 200L

/**
 * Milliseconds remaining until [targetAtMs], ticking off [clock] (never the wall clock, dossier
 * 5.3) so the round and next-round countdowns (dossier 6.2/6.3) stay correct across recompositions.
 * Zero when [clock] is not synced yet or [targetAtMs] is null.
 */
@Composable
fun rememberRemainingMs(targetAtMs: Long?, clock: ServerClock?): Long {
  var remaining by remember { mutableLongStateOf(0L) }
  LaunchedEffect(targetAtMs, clock) {
    while (isActive) {
      remaining = if (targetAtMs == null || clock == null) 0L else (targetAtMs - clock.nowMs()).coerceAtLeast(0L)
      delay(TickIntervalMs)
    }
  }
  return remaining
}
