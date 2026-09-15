// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.colman.palavramento.R
import br.com.colman.palavramento.clock.ServerClock
import br.com.colman.palavramento.state.MatchUiState
import br.com.colman.palavramento.ui.common.formatCountdown
import br.com.colman.palavramento.ui.common.rememberRemainingMs
import br.com.colman.palavramento.ui.theme.PalavramentoColors

private const val ResultsTabIndex = 0
private const val LeaderboardTabIndex = 1

/**
 * Post-round screen (dossie 6.3/6.4): the "Proxima partida em MM:SS" countdown on top, then a
 * Resultados/Placar tab pair. Both tabs read from the same [postRound] state, so switching tabs
 * never loses the round's data.
 */
@Composable
fun ResultsAndLeaderboardScreen(postRound: MatchUiState.PostRound, clock: ServerClock?) {
  val colors = PalavramentoColors.current
  var selectedTab by remember { mutableIntStateOf(ResultsTabIndex) }
  val remainingMs = rememberRemainingMs(postRound.nextRoundStartsAt, clock)

  Column(Modifier.fillMaxSize().background(colors.resultsPrimary)) {
    Text(
      stringResource(R.string.results_next_round_format, formatCountdown(remainingMs)),
      color = colors.textPrimary,
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(16.dp),
    )
    PrimaryTabRow(selectedTabIndex = selectedTab, containerColor = colors.surface, contentColor = colors.textPrimary) {
      Tab(
        selected = selectedTab == ResultsTabIndex,
        onClick = { selectedTab = ResultsTabIndex },
        text = { Text(stringResource(R.string.results_tab_results)) },
      )
      Tab(
        selected = selectedTab == LeaderboardTabIndex,
        onClick = { selectedTab = LeaderboardTabIndex },
        text = { Text(stringResource(R.string.results_tab_leaderboard)) },
        enabled = postRound.leaderboard != null,
      )
    }

    when (selectedTab) {
      LeaderboardTabIndex -> postRound.leaderboard?.let { LeaderboardScreen(it) }
      else -> ResultsScreen(
        board = postRound.board,
        mutator = postRound.mutator,
        stats = postRound.stats,
        maxScore = postRound.maxScore,
        maxWords = postRound.maxWords,
        words = postRound.words,
      )
    }
  }
}
