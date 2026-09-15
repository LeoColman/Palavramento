// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.colman.palavramento.R
import br.com.colman.palavramento.domain.protocol.RoundHistoryEntry
import br.com.colman.palavramento.ui.room.ResultsScreen
import br.com.colman.palavramento.ui.theme.PalavramentoColors
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Test tag for the history list's back button, used by instrumented navigation tests. */
const val HistoryBackButtonTestTag = "historyBackButton"

/**
 * History screen (dossier 7, task brief deliverable 3): the cached last [MaxCachedRounds][br.com.colman.palavramento.data.MaxCachedRounds]
 * rounds, reachable from the lobby. One route hosts both the list and the read-only detail (same
 * pattern as [br.com.colman.palavramento.ui.room.RoomScreen]'s sub-screens), so a tapped round never
 * needs its own NavHost destination or a Koin-parameterized view model just to carry one id across.
 */
@Composable
fun HistoryScreen(onBack: () -> Unit, viewModel: HistoryViewModel = koinViewModel()) {
  val rounds by viewModel.rounds.collectAsState()
  var selected by remember { mutableStateOf<RoundHistoryEntry?>(null) }
  val current = selected

  if (current != null) {
    HistoryDetailScreen(current, onBack = { selected = null })
  } else {
    HistoryListScreen(rounds, onBack = onBack, onRoundClicked = { selected = it })
  }
}

@Composable
private fun HistoryListScreen(
  rounds: List<RoundHistoryEntry>,
  onBack: () -> Unit,
  onRoundClicked: (RoundHistoryEntry) -> Unit
) {
  val colors = PalavramentoColors.current
  Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
    HistoryHeader(onBack)
    if (rounds.isEmpty()) {
      Text(
        stringResource(R.string.history_empty),
        color = colors.textSecondary,
        modifier = Modifier.padding(16.dp),
      )
    } else {
      LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(rounds, key = { it.roundId }) { round -> HistoryRow(round, onClick = { onRoundClicked(round) }) }
      }
    }
  }
}

@Composable
private fun HistoryHeader(onBack: () -> Unit) {
  val colors = PalavramentoColors.current
  val backDescription = stringResource(R.string.history_back_content_description)
  Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
    IconButton(
      onClick = onBack,
      modifier = Modifier.testTag(HistoryBackButtonTestTag).semantics { contentDescription = backDescription },
    ) {
      Text("<", color = colors.textPrimary)
    }
    Text(
      stringResource(R.string.history_title),
      color = colors.textPrimary,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(start = 8.dp),
    )
  }
}

@Composable
private fun HistoryRow(round: RoundHistoryEntry, onClick: () -> Unit) {
  val colors = PalavramentoColors.current
  Row(
    Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
      .background(colors.surface, RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .padding(12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column {
      Text(formatRoundDate(round.startsAt), color = colors.textSecondary)
      Text(round.themeTitle, color = colors.textPrimary, fontWeight = FontWeight.Bold)
    }
    Column(horizontalAlignment = Alignment.End) {
      Text(
        stringResource(R.string.results_points_format, round.stats.points, round.maxScore),
        color = colors.textPrimary
      )
      Text(stringResource(R.string.history_rank_format, round.rank, round.totalPlayers), color = colors.textSecondary)
    }
  }
}

/** Read-only Resultados view for one cached round (task brief: "reusing the existing Results components"). */
@Composable
private fun HistoryDetailScreen(round: RoundHistoryEntry, onBack: () -> Unit) {
  val colors = PalavramentoColors.current
  Column(Modifier.fillMaxSize().background(colors.resultsPrimary).windowInsetsPadding(WindowInsets.safeDrawing)) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onBack) { Text("<", color = colors.textPrimary) }
      Column(Modifier.padding(start = 8.dp)) {
        Text(formatRoundDate(round.startsAt), color = colors.textPrimary, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.history_rank_format, round.rank, round.totalPlayers), color = colors.textSecondary)
      }
    }
    Box(Modifier.weight(1f)) {
      ResultsScreen(
        board = round.board,
        stats = round.stats,
        maxScore = round.maxScore,
        maxWords = round.maxWords,
        words = round.words,
      )
    }
  }
}

private fun formatRoundDate(epochMs: Long): String =
  Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(HistoryDateFormat)

private val HistoryDateFormat: DateTimeFormatter =
  DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
