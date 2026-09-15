// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.colman.palavramento.R
import br.com.colman.palavramento.domain.protocol.LeaderboardRow
import br.com.colman.palavramento.domain.protocol.ServerMessage
import br.com.colman.palavramento.ui.theme.PalavramentoColors

/** Leaderboard tab content (dossie 6.4): self strip on top, "Jogadores Top" table below. */
@Composable
fun LeaderboardScreen(leaderboard: ServerMessage.Leaderboard) {
  val colors = PalavramentoColors.current
  Column(Modifier.fillMaxSize().background(colors.resultsPrimary).padding(16.dp)) {
    SelfStrip(leaderboard.self, leaderboard.percentile)
    Text(
      stringResource(R.string.leaderboard_top_players_title),
      color = colors.textPrimary,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
    LazyColumn(Modifier.weight(1f)) {
      items(leaderboard.players, key = { it.rank }) { row ->
        LeaderboardRowView(row, isSelf = row.rank == leaderboard.self.rank)
      }
    }
  }
}

@Composable
private fun SelfStrip(self: LeaderboardRow, percentile: Int) {
  val colors = PalavramentoColors.current
  Row(
    Modifier
      .fillMaxWidth()
      .background(colors.surface, RoundedCornerShape(12.dp))
      .padding(12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(stringResource(R.string.leaderboard_rank_format, self.rank), color = colors.textPrimary)
    Text(self.name, color = colors.textPrimary)
    Text(self.score.toString(), color = colors.textPrimary)
    Text(
      pluralStringResource(R.plurals.leaderboard_words_short, self.words, self.words),
      color = colors.textSecondary,
    )
    Text(stringResource(R.string.leaderboard_percentile_format, percentile), color = colors.highlight)
  }
}

@Composable
private fun LeaderboardRowView(row: LeaderboardRow, isSelf: Boolean) {
  val colors = PalavramentoColors.current
  Row(
    Modifier
      .fillMaxWidth()
      .background(if (isSelf) colors.surfaceVariant else colors.background, RoundedCornerShape(8.dp))
      .padding(horizontal = 12.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(stringResource(R.string.leaderboard_rank_format, row.rank), color = colors.textPrimary)
    Text(row.name, color = colors.textPrimary)
    Text(row.score.toString(), color = colors.textPrimary)
    Text(
      pluralStringResource(R.plurals.leaderboard_words_short, row.words, row.words),
      color = colors.textSecondary,
    )
  }
}
