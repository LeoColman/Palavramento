// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.colman.palavramento.R
import br.com.colman.palavramento.network.ConnectionStatus
import br.com.colman.palavramento.state.MatchUiState
import br.com.colman.palavramento.ui.theme.PalavramentoColors
import org.koin.androidx.compose.koinViewModel

/** Semantics test tag for the "Reconectando..." banner, used by instrumented tests. */
const val ReconnectingBannerTestTag = "reconnectingBanner"

/**
 * Room screen (task brief flow: Lobby -> (Waiting) -> Match -> Results/Leaderboard -> next round
 * automatically -> ...). One route hosts every room sub-screen because they share one
 * [RoomViewModel]/[MultiplayerSession][br.com.colman.palavramento.network.MultiplayerSession]
 * connection and the server drives the transitions between them; there is nothing for the player to
 * navigate between manually until they leave.
 *
 * Task brief 5 (unstable network): a [ConnectionStatus.Reconnecting] banner overlays whichever
 * sub-screen is current instead of replacing it, and [PauseAndResumeOnLifecycle] disconnects the
 * socket when the app is backgrounded and reconnects when it comes back, without leaking the
 * underlying loop coroutine.
 */
@Composable
fun RoomScreen(onLeaveRoom: () -> Unit, onOpenAbout: () -> Unit = {}, viewModel: RoomViewModel = koinViewModel()) {
  val state by viewModel.state.collectAsState()
  val clock by viewModel.clock.collectAsState()
  val connectionStatus by viewModel.connectionStatus.collectAsState()

  PauseAndResumeOnLifecycle(viewModel)

  BackHandler {
    viewModel.leaveRoom()
    onLeaveRoom()
  }

  Box(Modifier.fillMaxSize()) {
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
        onOpenAbout = onOpenAbout,
      )

      is MatchUiState.PostRound -> ResultsAndLeaderboardScreen(current, clock)
    }

    // Only shown once some room screen has actually been reached (Disconnected already has its own
    // full-screen indicator for the very first connect): a drop after that keeps the last screen on
    // screen (dossier 5.3, task brief 5) and just overlays this banner on top of it.
    if (state !is MatchUiState.Disconnected && connectionStatus == ConnectionStatus.Reconnecting) {
      ReconnectingBanner(Modifier.align(Alignment.TopCenter))
    }
  }
}

/**
 * Disconnects [viewModel]'s session when the app leaves the foreground and reconnects when it
 * returns (task brief 5), on top of the room-leaves-the-back-stack handling already in
 * [RoomViewModel.onCleared]. `Lifecycle.addObserver` synchronously replays past events up to the
 * owner's current state, so this can call [RoomViewModel.resume] again right after
 * [RoomViewModel]'s own `init` already started the loop; [RoomViewModel.start] is a no-op when the
 * loop is already running, so that extra call is harmless.
 */
@Composable
private fun PauseAndResumeOnLifecycle(viewModel: RoomViewModel) {
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, viewModel) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_STOP -> viewModel.pause()
        Lifecycle.Event.ON_START -> viewModel.resume()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
}

@Composable
private fun ReconnectingBanner(modifier: Modifier = Modifier) {
  val colors = PalavramentoColors.current
  Text(
    stringResource(R.string.room_reconnecting),
    color = colors.textPrimary,
    textAlign = TextAlign.Center,
    modifier = modifier
      .testTag(ReconnectingBannerTestTag)
      .fillMaxWidth()
      .background(colors.rejected)
      .padding(8.dp),
  )
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
