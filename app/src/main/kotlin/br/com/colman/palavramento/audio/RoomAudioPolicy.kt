// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.audio

import br.com.colman.palavramento.state.MatchUiState
import br.com.colman.palavramento.state.SubmissionFeedback

/**
 * When the background music should start/stop, and which sound effect (if any) a fresh submission
 * result calls for - pure decisions over [MatchUiState] (task brief: "when music should play given
 * the room state"), independent of any actual [GameAudio] so both are unit-testable without Android.
 * [br.com.colman.palavramento.ui.room.RoomViewModel] is the only caller: it tracks the round the
 * music is currently playing for (or null) and the last feedback it already played a sound for, and
 * folds every new [MatchUiState] through these two functions.
 */
object RoomAudioPolicy {

  /** What [GameAudio] should be told to do about the music track for a new state. */
  sealed interface MusicAction {
    /** Start the track for [roundId] (there was none playing, or it was for a different round). */
    data class Start(val roundId: String) : MusicAction

    /** Stop the track: the room left [MatchUiState.InRound]. */
    data object Stop : MusicAction

    /** Nothing changed: already playing this exact round, or already stopped. */
    data object None : MusicAction
  }

  /**
   * [currentRoundId] is the round the music is currently playing for, or null while stopped. A
   * reconnect's fresh `RoundStart` for the very same round (task brief: "must not restart the track
   * from zero") folds into an [MatchUiState.InRound] whose `roundId` is unchanged, so this returns
   * [MusicAction.None] rather than [MusicAction.Start] - the caller never even calls
   * [GameAudio.startMusic] again for it.
   */
  fun musicAction(currentRoundId: String?, state: MatchUiState): MusicAction = when {
    state is MatchUiState.InRound && state.roundId != currentRoundId -> MusicAction.Start(state.roundId)
    state !is MatchUiState.InRound && currentRoundId != null -> MusicAction.Stop
    else -> MusicAction.None
  }

  /**
   * The [SoundEffect] to play for [state]'s current feedback, if it is genuinely new (different
   * from [previousFeedback], the last one already played a sound for) - not a recomposition or a
   * repeated collection of the very same `WordAccepted`/`WordRejected`. Null outside a round, when
   * there is no feedback yet, or when nothing changed.
   */
  fun effectAction(previousFeedback: SubmissionFeedback?, state: MatchUiState): SoundEffect? {
    val feedback = (state as? MatchUiState.InRound)?.lastFeedback
    return feedback?.takeIf { it != previousFeedback }?.toSoundEffect()
  }
}
