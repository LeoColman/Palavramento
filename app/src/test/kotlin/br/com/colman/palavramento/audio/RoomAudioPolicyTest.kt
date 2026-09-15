// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.audio

import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.stats.RoundStats
import br.com.colman.palavramento.domain.submission.RejectionReason
import br.com.colman.palavramento.state.MatchUiState
import br.com.colman.palavramento.state.SubmissionFeedback
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private fun inRound(roundId: String = "round-1", lastFeedback: SubmissionFeedback? = null) = MatchUiState.InRound(
  roundId = roundId,
  board = emptyList(),
  mutator = Mutator.NoMutator,
  themeTitle = "Grade padrao",
  themeSubtitle = "15 palavras comuns",
  maxScore = 100,
  maxWords = 10,
  startsAt = 0,
  endsAt = 120_000,
  foundWords = emptyList(),
  runningScore = 0,
  runningWords = 0,
  lastFeedback = lastFeedback,
)

private fun postRound(roundId: String = "round-1") = MatchUiState.PostRound(
  roundId = roundId,
  board = emptyList(),
  mutator = Mutator.NoMutator,
  stats = RoundStats(0, 0, 0.0, 0.0, 0, 0.0, 0),
  words = emptyList(),
  maxScore = 100,
  maxWords = 10,
)

class RoomAudioPolicyTest : FunSpec({

  context("musicAction") {

    test("Entering InRound for the first time starts the music") {
      RoomAudioPolicy.musicAction(null, inRound("round-1")) shouldBe RoomAudioPolicy.MusicAction.Start("round-1")
    }

    test("A fresh RoundStart for the same round (reconnect) does not restart the track") {
      RoomAudioPolicy.musicAction("round-1", inRound("round-1")) shouldBe RoomAudioPolicy.MusicAction.None
    }

    test("A new round while a different one was playing restarts for the new round") {
      RoomAudioPolicy.musicAction("round-1", inRound("round-2")) shouldBe RoomAudioPolicy.MusicAction.Start("round-2")
    }

    test("Leaving InRound (round end) stops the music") {
      RoomAudioPolicy.musicAction("round-1", postRound("round-1")) shouldBe RoomAudioPolicy.MusicAction.Stop
    }

    test("Nothing to stop when the music was never playing") {
      RoomAudioPolicy.musicAction(null, postRound("round-1")) shouldBe RoomAudioPolicy.MusicAction.None
      RoomAudioPolicy.musicAction(null, MatchUiState.Disconnected) shouldBe RoomAudioPolicy.MusicAction.None
      RoomAudioPolicy.musicAction(null, MatchUiState.Lobby(0, 0)) shouldBe RoomAudioPolicy.MusicAction.None
    }
  }

  context("effectAction") {

    test("No feedback yet plays nothing") {
      RoomAudioPolicy.effectAction(null, inRound()) shouldBe null
    }

    test("Outside a round, nothing plays even if feedback is somehow present") {
      RoomAudioPolicy.effectAction(null, MatchUiState.Disconnected) shouldBe null
      RoomAudioPolicy.effectAction(null, postRound()) shouldBe null
    }

    test("A fresh Accepted feedback plays the Accepted effect") {
      val feedback = SubmissionFeedback.Accepted("CASA", 6, listOf(0, 1, 2, 3))
      RoomAudioPolicy.effectAction(null, inRound(lastFeedback = feedback)) shouldBe SoundEffect.Accepted
    }

    test("A fresh AlreadyFound rejection plays its own effect") {
      val feedback = SubmissionFeedback.Rejected(RejectionReason.AlreadyFound, listOf(0, 1))
      RoomAudioPolicy.effectAction(null, inRound(lastFeedback = feedback)) shouldBe SoundEffect.AlreadyFound
    }

    test("The same feedback as before (no new submission) plays nothing again") {
      val feedback = SubmissionFeedback.Accepted("CASA", 6, listOf(0, 1, 2, 3))
      RoomAudioPolicy.effectAction(feedback, inRound(lastFeedback = feedback)) shouldBe null
    }

    test("A different feedback than before plays again") {
      val first = SubmissionFeedback.Accepted("CASA", 6, listOf(0, 1, 2, 3))
      val second = SubmissionFeedback.Rejected(RejectionReason.NotAWord, listOf(4, 5))
      RoomAudioPolicy.effectAction(first, inRound(lastFeedback = second)) shouldBe SoundEffect.Rejected
    }
  }
})
