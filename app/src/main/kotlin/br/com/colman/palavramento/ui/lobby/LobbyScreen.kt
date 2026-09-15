// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.palavramento.R
import br.com.colman.palavramento.domain.protocol.LifetimeStats
import br.com.colman.palavramento.domain.protocol.PlayerProfile
import br.com.colman.palavramento.ui.theme.PalavramentoColors
import org.koin.androidx.compose.koinViewModel

private const val DimmedAlpha = 0.4f

/** Lobby screen (dossie 6.1): header, lifetime stats, language selector (locked), Jogar. */
@Composable
fun LobbyScreen(onPlayClicked: () -> Unit, viewModel: LobbyViewModel = koinViewModel()) {
  val uiState by viewModel.uiState.collectAsState()
  val colors = PalavramentoColors.current

  Column(
    Modifier
      .fillMaxSize()
      .background(colors.background)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    LobbyHeader(uiState.profile)
    StatsPanel(uiState.stats, uiState.isGuest, onLoginClicked = viewModel::onLoginClicked)
    LanguageSelector()
    Button(onClick = onPlayClicked, modifier = Modifier.fillMaxWidth()) {
      Text(stringResource(R.string.lobby_play_button))
    }
  }
}

@Composable
private fun LobbyHeader(profile: PlayerProfile?) {
  val colors = PalavramentoColors.current
  Row(
    Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      Modifier
        .size(56.dp)
        .background(colors.surfaceVariant, CircleShape),
    )
    Column {
      Text(profile?.displayName ?: stringResource(R.string.lobby_guest_name), color = colors.textPrimary)
      Text(
        stringResource(R.string.lobby_level_format, profile?.level ?: 0),
        color = colors.textSecondary,
      )
      Text(
        stringResource(R.string.lobby_xp_format, profile?.totalXp ?: 0L, profile?.xpForNextLevel ?: 0L),
        color = colors.textSecondary,
      )
    }
  }
}

@Composable
private fun StatsPanel(stats: LifetimeStats?, isGuest: Boolean, onLoginClicked: () -> Unit) {
  val colors = PalavramentoColors.current
  Column(
    Modifier
      .fillMaxWidth()
      .background(colors.surface, RoundedCornerShape(12.dp))
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(stringResource(R.string.lobby_stats_title), color = colors.textPrimary)
    Column(
      Modifier.alpha(if (isGuest) DimmedAlpha else 1f),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      val empty = stringResource(R.string.lobby_stat_empty)
      StatRow(stringResource(R.string.lobby_stat_total_score), stats?.totalScore?.toString() ?: empty)
      StatRow(stringResource(R.string.lobby_stat_total_words), stats?.totalWords?.toString() ?: empty)
      StatRow(stringResource(R.string.lobby_stat_best_game_score), stats?.bestGameScore?.toString() ?: empty)
      StatRow(stringResource(R.string.lobby_stat_best_word), stats?.bestWord ?: empty)
      StatRow(stringResource(R.string.lobby_stat_games_completed), stats?.gamesCompleted?.toString() ?: empty)
      StatRow(stringResource(R.string.lobby_stat_average_score), stats?.averageScore?.toString() ?: empty)
      StatRow(stringResource(R.string.lobby_stat_average_words), stats?.averageWords?.toString() ?: empty)
      StatRow(
        stringResource(R.string.lobby_stat_average_points_per_word),
        stats?.averagePointsPerWord?.toString() ?: empty,
      )
      StatRow(stringResource(R.string.lobby_stat_best_rank), stats?.bestRank?.toString() ?: empty)
      StatRow(stringResource(R.string.lobby_stat_games_played), stats?.gamesPlayed?.toString() ?: empty)
    }
    if (isGuest) {
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(stringResource(R.string.lobby_guest_invite), color = colors.textSecondary)
        Button(onClick = onLoginClicked) {
          Text(stringResource(R.string.lobby_login_button))
        }
      }
    }
  }
}

@Composable
private fun StatRow(label: String, value: String) {
  val colors = PalavramentoColors.current
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, color = colors.textSecondary)
    Text(value, color = colors.textPrimary)
  }
}

@Composable
private fun LanguageSelector() {
  val colors = PalavramentoColors.current
  Row(
    Modifier
      .fillMaxWidth()
      .background(colors.surface, RoundedCornerShape(12.dp))
      .padding(16.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(stringResource(R.string.lobby_language_label), color = colors.textSecondary)
    Text(stringResource(R.string.lobby_language_value), color = colors.textDisabled)
  }
}
