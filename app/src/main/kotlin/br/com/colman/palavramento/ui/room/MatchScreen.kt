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
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.colman.palavramento.R
import br.com.colman.palavramento.clock.ServerClock
import br.com.colman.palavramento.domain.board.Rotation
import br.com.colman.palavramento.state.MatchUiState
import br.com.colman.palavramento.state.SubmissionFeedback
import br.com.colman.palavramento.ui.common.formatCountdown
import br.com.colman.palavramento.ui.common.rememberRemainingMs
import br.com.colman.palavramento.ui.theme.PalavramentoColors

/** Match screen (dossie 6.2): header, countdown, score strip, board, gesture, feedback, Girar. */
@Composable
fun MatchScreen(
  round: MatchUiState.InRound,
  clock: ServerClock?,
  onSubmit: (roundId: String, path: List<Int>, clientTimestampMs: Long) -> Unit,
  onBack: () -> Unit,
) {
  val colors = PalavramentoColors.current
  val haptics = LocalHapticFeedback.current
  var rotation by remember { mutableStateOf(Rotation.Deg0) }
  val remainingMs = rememberRemainingMs(round.endsAt, clock)
  val currentRound = rememberUpdatedState(round)

  LaunchedEffect(round.lastFeedback) {
    val feedback = round.lastFeedback ?: return@LaunchedEffect
    val type = if (feedback is SubmissionFeedback.Accepted) HapticFeedbackType.Confirm else HapticFeedbackType.Reject
    haptics.performHapticFeedback(type)
  }

  Column(
    Modifier
      .fillMaxSize()
      .background(colors.matchPrimary)
      .padding(16.dp),
  ) {
    MatchHeader(round.themeTitle, round.themeSubtitle, onBack)
    Text(
      formatCountdown(remainingMs),
      color = colors.textPrimary,
      fontSize = 48.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(vertical = 8.dp),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(stringResource(R.string.match_points_format, round.runningScore, round.maxScore), color = colors.textPrimary)
      Text(stringResource(R.string.match_words_format, round.runningWords, round.maxWords), color = colors.textPrimary)
    }

    BoardView(
      tiles = round.board,
      mutator = round.mutator,
      rotation = rotation,
      onSubmit = { path ->
        val timestamp = clock?.nowMs() ?: System.currentTimeMillis()
        onSubmit(currentRound.value.roundId, path, timestamp)
      },
      modifier = Modifier.padding(top = 16.dp),
    )

    FeedbackRow(round.lastFeedback)

    Button(
      onClick = { rotation = rotation.rotatedClockwise() },
      modifier = Modifier.padding(top = 16.dp),
    ) {
      Text(stringResource(R.string.match_rotate_button))
    }
  }
}

@Composable
private fun MatchHeader(themeTitle: String, themeSubtitle: String, onBack: () -> Unit) {
  val colors = PalavramentoColors.current
  val backDescription = stringResource(R.string.match_back_content_description)
  val settingsDescription = stringResource(R.string.match_settings_content_description)
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    IconButton(
      onClick = onBack,
      modifier = Modifier
        .testTag(MatchBackButtonTestTag)
        .semantics { contentDescription = backDescription },
    ) {
      Text("<", color = colors.textPrimary)
    }
    Column(Modifier.padding(horizontal = 8.dp).weight(1f)) {
      Text(themeTitle, color = colors.textPrimary, fontWeight = FontWeight.Bold)
      Text(themeSubtitle, color = colors.textSecondary)
    }
    IconButton(
      onClick = {},
      modifier = Modifier.semantics { contentDescription = settingsDescription },
    ) {
      Text("*", color = colors.textPrimary)
    }
  }
}

@Composable
private fun FeedbackRow(feedback: SubmissionFeedback?) {
  val colors = PalavramentoColors.current
  val text = when (feedback) {
    is SubmissionFeedback.Accepted -> stringResource(R.string.match_word_accepted_format, feedback.word, feedback.score)
    is SubmissionFeedback.Rejected -> stringResource(feedback.reason.messageRes())
    null -> ""
  }
  val color = when (feedback) {
    is SubmissionFeedback.Accepted -> colors.accepted
    is SubmissionFeedback.Rejected -> colors.rejected
    null -> colors.textSecondary
  }
  Text(text, color = color, modifier = Modifier.padding(top = 8.dp).testTag(MatchFeedbackTestTag))
}

/** Test tag for the back button, used by instrumented navigation tests. */
const val MatchBackButtonTestTag = "matchBackButton"

/** Test tag for the last accept/reject feedback line, used by instrumented gesture tests. */
const val MatchFeedbackTestTag = "matchFeedback"

private fun Rotation.rotatedClockwise(): Rotation {
  val values = Rotation.entries
  return values[(ordinal + 1) % values.size]
}
