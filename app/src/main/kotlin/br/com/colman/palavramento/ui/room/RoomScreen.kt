// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.palavramento.R
import br.com.colman.palavramento.state.MatchUiState
import br.com.colman.palavramento.ui.theme.PalavramentoColors
import org.koin.androidx.compose.koinViewModel

/**
 * Room screen (task brief flow: Lobby -> (Waiting) -> Match -> Results/Leaderboard -> next round
 * automatically -> ...). One route hosts every room sub-screen because they share one
 * [RoomViewModel]/[MultiplayerSession][br.com.colman.palavramento.network.MultiplayerSession]
 * connection and the server drives the transitions between them; there is nothing for the player to
 * navigate between manually until they leave.
 */
@Composable
fun RoomScreen(onLeaveRoom: () -> Unit, viewModel: RoomViewModel = koinViewModel()) {
  val state by viewModel.state.collectAsState()
  val clock by viewModel.clock.collectAsState()

  BackHandler {
    viewModel.leaveRoom()
    onLeaveRoom()
  }

  when (val current = state) {
    is MatchUiState.Disconnected -> ConnectingIndicator()
    is MatchUiState.Lobby -> WaitingScreen(current.nextRoundStartsAt, current.playersWaiting, clock)
    is MatchUiState.InRound -> MatchScreen(
      round = current,
      clock = clock,
      onSubmit = viewModel::submitWord,
      onBack = {
        viewModel.leaveRoom()
        onLeaveRoom()
      },
    )

    is MatchUiState.PostRound -> ResultsAndLeaderboardScreen(current, clock)
  }
}

@Composable
private fun ConnectingIndicator() {
  val colors = PalavramentoColors.current
  Column(
    Modifier.fillMaxSize().background(colors.background),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    CircularProgressIndicator(color = colors.matchAccent)
    Text(
      stringResource(R.string.room_connecting),
      color = colors.textSecondary,
      modifier = Modifier.padding(top = 8.dp),
    )
  }
}
