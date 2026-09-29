// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.colman.palavramento.R
import br.com.colman.palavramento.clock.ServerClock
import br.com.colman.palavramento.ui.common.rememberRemainingMs
import br.com.colman.palavramento.ui.theme.PalavramentoColors

/** Test tag for the guessing lock over the board, used by instrumented tests. */
const val GuessLockTestTag = "guessLock"

private const val ScrimAlpha = 0.85f
private const val MillisPerSecond = 1_000L

/**
 * Covers the board while it is locked for guessing (GuessGuard): says why, counts the seconds down
 * off [clock], and swallows every touch so no path can be traced underneath. Nothing at all once
 * [lockedUntilMs] has passed.
 */
@Composable
fun GuessLockOverlay(lockedUntilMs: Long?, clock: ServerClock?, modifier: Modifier = Modifier) {
  val remainingMs = rememberRemainingMs(lockedUntilMs, clock)
  if (remainingMs <= 0L) return
  val seconds = ((remainingMs + MillisPerSecond - 1) / MillisPerSecond).toInt()
  val colors = PalavramentoColors.current
  Column(
    modifier
      .testTag(GuessLockTestTag)
      .background(colors.matchPrimary.copy(alpha = ScrimAlpha))
      .pointerInput(Unit) {
        awaitEachGesture {
          awaitFirstDown(requireUnconsumed = false).consume()
          do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
          } while (event.changes.any { it.pressed })
        }
      },
    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(stringResource(R.string.match_guess_lock_title), color = colors.textPrimary, fontWeight = FontWeight.Bold)
    Text(pluralStringResource(R.plurals.match_guess_lock_wait, seconds, seconds), color = colors.textSecondary)
  }
}
