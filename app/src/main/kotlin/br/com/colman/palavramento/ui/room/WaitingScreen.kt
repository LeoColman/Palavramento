// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.colman.palavramento.R
import br.com.colman.palavramento.clock.ServerClock
import br.com.colman.palavramento.ui.common.FlipCountdown
import br.com.colman.palavramento.ui.common.SmallDigitSize
import br.com.colman.palavramento.ui.common.formatCountdown
import br.com.colman.palavramento.ui.common.rememberRemainingMs
import br.com.colman.palavramento.ui.theme.PalavramentoColors

/** Between-rounds screen (dossie 6.1): next round countdown and players waiting. */
@Composable
fun WaitingScreen(nextRoundStartsAt: Long, playersWaiting: Int, clock: ServerClock?) {
  val colors = PalavramentoColors.current
  val remainingMs = rememberRemainingMs(nextRoundStartsAt, clock)
  // formatCountdown still backs the accessible description of the label+flip-digits row below; the
  // flip digits themselves are the visible MM:SS (task brief 4).
  val description = stringResource(R.string.waiting_next_round_format, formatCountdown(remainingMs))

  Column(
    Modifier.fillMaxSize().background(colors.background),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
      Text(
        stringResource(R.string.next_round_label),
        color = colors.textPrimary,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
      )
      Spacer(Modifier.width(8.dp))
      FlipCountdown(remainingMs, digitSize = SmallDigitSize)
    }
    Text(
      pluralStringResource(R.plurals.waiting_players_count, playersWaiting, playersWaiting),
      color = colors.textSecondary,
      modifier = Modifier.padding(top = 8.dp),
    )
  }
}
