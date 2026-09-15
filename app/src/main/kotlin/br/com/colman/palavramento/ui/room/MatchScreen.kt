// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import br.com.colman.palavramento.R
import br.com.colman.palavramento.clock.ServerClock
import br.com.colman.palavramento.domain.board.Rotation
import br.com.colman.palavramento.domain.submission.RejectionReason
import br.com.colman.palavramento.state.MatchUiState
import br.com.colman.palavramento.state.SubmissionFeedback
import br.com.colman.palavramento.ui.common.FlipCountdown
import br.com.colman.palavramento.ui.common.LargeDigitSize
import br.com.colman.palavramento.ui.common.animationDurationMillis
import br.com.colman.palavramento.ui.common.rememberRemainingMs
import br.com.colman.palavramento.ui.settings.MatchSettingsSheet
import br.com.colman.palavramento.ui.settings.SettingsViewModel
import br.com.colman.palavramento.ui.theme.PalavramentoColors
import org.koin.androidx.compose.koinViewModel

/** Match screen (dossie 6.2): header, countdown, score strip, board, gesture, feedback, Girar. */
@Composable
fun MatchScreen(
  round: MatchUiState.InRound,
  clock: ServerClock?,
  onSubmit: (roundId: String, path: List<Int>, clientTimestampMs: Long) -> Unit,
  onBack: () -> Unit,
  onOpenAbout: () -> Unit = {},
  settingsViewModel: SettingsViewModel = koinViewModel(),
) {
  val colors = PalavramentoColors.current
  val hapticsEnabled by settingsViewModel.hapticsEnabled.collectAsState()
  val rotationController = rememberRotationController()
  var showSettings by remember { mutableStateOf(false) }
  val remainingMs = rememberRemainingMs(round.endsAt, clock)
  val currentRound = rememberUpdatedState(round)

  PlayFeedbackHaptics(round.lastFeedback, hapticsEnabled)

  Column(
    Modifier
      .fillMaxSize()
      .background(colors.matchPrimary)
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(16.dp),
  ) {
    MatchHeader(round.themeTitle, round.themeSubtitle, onBack, onOpenSettings = { showSettings = true })
    FlipCountdown(remainingMs, digitSize = LargeDigitSize, modifier = Modifier.padding(vertical = 8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(stringResource(R.string.match_points_format, round.runningScore, round.maxScore), color = colors.textPrimary)
      Text(stringResource(R.string.match_words_format, round.runningWords, round.maxWords), color = colors.textPrimary)
    }

    BoardView(
      tiles = round.board,
      mutator = round.mutator,
      rotation = rotationController.rotation,
      visualRotationDegrees = rotationController.visualDegrees,
      feedback = round.lastFeedback,
      hapticsEnabled = hapticsEnabled,
      onSubmit = { path ->
        val timestamp = clock?.nowMs() ?: System.currentTimeMillis()
        onSubmit(currentRound.value.roundId, path, timestamp)
      },
      modifier = Modifier.padding(top = 16.dp),
    )

    FeedbackRow(round.lastFeedback)

    Button(onClick = rotationController.rotate, modifier = Modifier.padding(top = 16.dp)) {
      Text(stringResource(R.string.match_rotate_button))
    }
  }

  if (showSettings) {
    MatchSettingsSheet(
      onDismiss = { showSettings = false },
      onAboutClick = {
        showSettings = false
        onOpenAbout()
      },
    )
  }
}

@Composable
private fun MatchHeader(themeTitle: String, themeSubtitle: String, onBack: () -> Unit, onOpenSettings: () -> Unit) {
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
      onClick = onOpenSettings,
      modifier = Modifier
        .testTag(MatchSettingsButtonTestTag)
        .semantics { contentDescription = settingsDescription },
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
    is SubmissionFeedback.Rejected ->
      if (feedback.reason == RejectionReason.AlreadyFound) colors.duplicate else colors.rejected
    null -> colors.textSecondary
  }
  Text(text, color = color, modifier = Modifier.padding(top = 8.dp).testTag(MatchFeedbackTestTag))
}

/** Test tag for the back button, used by instrumented navigation tests. */
const val MatchBackButtonTestTag = "matchBackButton"

/** Test tag for the settings gear button, used by instrumented tests. */
const val MatchSettingsButtonTestTag = "matchSettingsButton"

/** Test tag for the last accept/reject feedback line, used by instrumented gesture tests. */
const val MatchFeedbackTestTag = "matchFeedback"

/** Plays the accept/reject haptic for [feedback] (task brief 3), gated by [hapticsEnabled]. */
@Composable
private fun PlayFeedbackHaptics(feedback: SubmissionFeedback?, hapticsEnabled: Boolean) {
  val haptics = LocalHapticFeedback.current
  LaunchedEffect(feedback) {
    val current = feedback ?: return@LaunchedEffect
    if (!hapticsEnabled) return@LaunchedEffect
    val type = if (current is SubmissionFeedback.Accepted) HapticFeedbackType.Confirm else HapticFeedbackType.Reject
    haptics.performHapticFeedback(type)
  }
}

/**
 * [rotation] (logical, fed to [BoardView]) and [visualDegrees] (cosmetic offset), plus the action to
 * trigger one clockwise quarter turn.
 */
private class RotationController(val rotation: Rotation, val visualDegrees: Float, val rotate: () -> Unit)

/**
 * One quarter-turn counter drives both halves of "Girar" in the same frame: the logical [Rotation]
 * switches at once, and [RotationController.visualDegrees] starts at -90 (the new arrangement turned
 * back, the very picture shown before the tap) and animates to 0.
 *
 * Nothing is snapped. Switching the arrangement after a spin and then snapping the spin back takes
 * two frames, and the frame in between shows the new arrangement still spun by 90 degrees: the board
 * upside down for an instant. Taps during a turn just add another quarter.
 */
@Composable
private fun rememberRotationController(): RotationController {
  var quarterTurns by remember { mutableIntStateOf(0) }
  val targetDegrees = quarterTurns * RotationStepDegrees
  val animatedDegrees by animateFloatAsState(
    targetValue = targetDegrees,
    animationSpec = tween(animationDurationMillis(RotationAnimationMillis)),
    label = "boardRotation",
  )
  val rotation = Rotation.entries[quarterTurns % Rotation.entries.size]
  return RotationController(rotation, animatedDegrees - targetDegrees) { quarterTurns++ }
}

private const val RotationStepDegrees = 90f
private const val RotationAnimationMillis = 300
