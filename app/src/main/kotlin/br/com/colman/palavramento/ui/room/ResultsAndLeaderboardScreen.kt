// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.colman.palavramento.R
import br.com.colman.palavramento.clock.ServerClock
import br.com.colman.palavramento.state.MatchUiState
import br.com.colman.palavramento.ui.common.FlipCountdown
import br.com.colman.palavramento.ui.common.SmallDigitSize
import br.com.colman.palavramento.ui.common.formatCountdown
import br.com.colman.palavramento.ui.common.rememberRemainingMs
import br.com.colman.palavramento.ui.theme.PalavramentoColors

/**
 * Post-round screen (dossie 6.3/6.4): the "Proxima partida em MM:SS" countdown on top, then a
 * Resultados/Placar tab pair. Both tabs read from the same [postRound] state, so switching tabs
 * never loses the round's data.
 *
 * The screen opens on Resultados and, halfway through the intermission, shows Placar by itself (see
 * [shouldAutoShowLeaderboard]). Which tab is displayed is derived, not stored: a tab the player taps
 * wins from then on, so the automatic flip can never fight a deliberate choice.
 */
@Composable
fun ResultsAndLeaderboardScreen(postRound: MatchUiState.PostRound, clock: ServerClock?) {
  val colors = PalavramentoColors.current
  var chosenTab by remember { mutableStateOf(PostRoundTab.Results) }
  var playerChoseTab by remember { mutableStateOf(false) }
  val remainingMs = rememberRemainingMs(postRound.nextRoundStartsAt, clock)
  val intermissionMs = rememberIntermissionMs(postRound.nextRoundStartsAt, clock)
  val autoShowLeaderboard =
    shouldAutoShowLeaderboard(remainingMs, intermissionMs, postRound.leaderboard != null, playerChoseTab)
  val selectedTab = if (autoShowLeaderboard) PostRoundTab.Leaderboard else chosenTab

  Column(Modifier.fillMaxSize().background(colors.resultsPrimary).windowInsetsPadding(WindowInsets.safeDrawing)) {
    val description = stringResource(R.string.results_next_round_format, formatCountdown(remainingMs))
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(16.dp).semantics(mergeDescendants = true) { contentDescription = description },
    ) {
      Text(stringResource(R.string.next_round_label), color = colors.textPrimary, fontWeight = FontWeight.Bold)
      Spacer(Modifier.width(8.dp))
      FlipCountdown(remainingMs, digitSize = SmallDigitSize)
    }
    PrimaryTabRow(
      selectedTabIndex = selectedTab.ordinal,
      containerColor = colors.surface,
      contentColor = colors.textPrimary,
    ) {
      Tab(
        selected = selectedTab == PostRoundTab.Results,
        onClick = {
          chosenTab = PostRoundTab.Results
          playerChoseTab = true
        },
        text = { Text(stringResource(R.string.results_tab_results)) },
      )
      Tab(
        selected = selectedTab == PostRoundTab.Leaderboard,
        onClick = {
          chosenTab = PostRoundTab.Leaderboard
          playerChoseTab = true
        },
        text = { Text(stringResource(R.string.results_tab_leaderboard)) },
        enabled = postRound.leaderboard != null,
      )
    }

    when (selectedTab) {
      PostRoundTab.Leaderboard -> postRound.leaderboard?.let { LeaderboardScreen(it) }
      PostRoundTab.Results -> ResultsScreen(
        board = postRound.board,
        stats = postRound.stats,
        maxScore = postRound.maxScore,
        maxWords = postRound.maxWords,
        words = postRound.words,
      )
    }
  }
}

/**
 * The whole intermission in milliseconds, measured from [clock] the moment `LobbyState` first
 * announces [nextRoundStartsAt] (the server broadcasts it right after `RoundEnd`, so that first
 * reading is the full wait, not a fraction of it). Zero until both are known, which keeps
 * [shouldAutoShowLeaderboard] from flipping tabs on a countdown it cannot measure yet.
 *
 * Measured exactly once per [nextRoundStartsAt], never again: a clock resync republishes a new
 * [ServerClock] every 20 seconds (`ClockSyncSettings.resyncIntervalMs`), well inside a 25 second
 * intermission, and measuring again then would read the time still left as if it were the whole
 * wait, pushing the halfway point most of the way to the next round.
 */
@Composable
private fun rememberIntermissionMs(nextRoundStartsAt: Long?, clock: ServerClock?): Long {
  var intermissionMs by remember(nextRoundStartsAt) { mutableLongStateOf(0L) }
  LaunchedEffect(nextRoundStartsAt, clock) {
    if (intermissionMs == 0L && nextRoundStartsAt != null && clock != null) {
      intermissionMs = (nextRoundStartsAt - clock.nowMs()).coerceAtLeast(0L)
    }
  }
  return intermissionMs
}
