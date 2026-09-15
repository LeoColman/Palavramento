// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.colman.palavramento.R
import br.com.colman.palavramento.domain.protocol.LifetimeStats
import br.com.colman.palavramento.domain.protocol.PlayerProfile
import br.com.colman.palavramento.ui.theme.PalavramentoColors
import org.koin.androidx.compose.koinViewModel

private const val DimmedAlpha = 0.4f

/** Test tag for the lobby's load-error retry button, used by instrumented tests. */
const val LobbyRetryButtonTestTag = "lobbyRetryButton"

/** Lobby screen (dossie 6.1): header, lifetime stats, language selector (locked), Jogar. */
@Composable
fun LobbyScreen(
  onPlayClicked: () -> Unit,
  onLoginClicked: () -> Unit,
  onHistoryClicked: () -> Unit,
  viewModel: LobbyViewModel = koinViewModel(),
) {
  val uiState by viewModel.uiState.collectAsState()
  val colors = PalavramentoColors.current

  // Task brief 2 ("sync... when the lobby opens") + orchestrator finding (retry on ON_RESUME):
  // re-syncs every time this screen becomes visible again, including returning from Login/History/Room.
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, viewModel) {
    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  Column(
    Modifier
      .fillMaxSize()
      .background(colors.background)
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    LobbyHeader(uiState.profile, uiState.isGuest, onLogoutClicked = viewModel::onLogoutClicked)
    TextButton(onClick = onHistoryClicked) { Text(stringResource(R.string.lobby_history_button)) }
    if (uiState.loadError && uiState.profile == null) {
      LoadErrorState(onRetry = viewModel::refresh)
    } else {
      StatsPanel(uiState.stats, uiState.isGuest, uiState.isLoading, onLoginClicked = onLoginClicked)
    }
    LanguageSelector()
    Button(onClick = onPlayClicked, modifier = Modifier.fillMaxWidth()) {
      Text(stringResource(R.string.lobby_play_button))
    }
  }
}

@Composable
private fun LoadErrorState(onRetry: () -> Unit) {
  val colors = PalavramentoColors.current
  Column(
    Modifier
      .fillMaxWidth()
      .background(colors.surface, RoundedCornerShape(12.dp))
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(stringResource(R.string.lobby_load_error), color = colors.textPrimary)
    Button(onClick = onRetry, modifier = Modifier.testTag(LobbyRetryButtonTestTag)) {
      Text(stringResource(R.string.lobby_retry_button))
    }
  }
}

@Composable
private fun LobbyHeader(profile: PlayerProfile?, isGuest: Boolean, onLogoutClicked: () -> Unit) {
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
    Column(Modifier.weight(1f)) {
      Text(profile?.displayName ?: stringResource(R.string.lobby_guest_name), color = colors.textPrimary)
      Text(
        stringResource(R.string.lobby_level_format, profile?.level ?: 1),
        color = colors.textSecondary,
      )
      Text(
        stringResource(R.string.lobby_xp_format, profile?.totalXp ?: 0L, profile?.xpForNextLevel ?: 100L),
        color = colors.textSecondary,
      )
    }
    if (!isGuest) {
      TextButton(onClick = onLogoutClicked) { Text(stringResource(R.string.lobby_logout_button)) }
    }
  }
}

@Composable
private fun StatsPanel(stats: LifetimeStats?, isGuest: Boolean, isLoading: Boolean, onLoginClicked: () -> Unit) {
  val colors = PalavramentoColors.current
  Column(
    Modifier
      .fillMaxWidth()
      .background(colors.surface, RoundedCornerShape(12.dp))
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(stringResource(R.string.lobby_stats_title), color = colors.textPrimary)
      if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colors.textSecondary)
    }
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
