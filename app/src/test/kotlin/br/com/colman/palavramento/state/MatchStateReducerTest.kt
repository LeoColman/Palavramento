// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.state

import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.protocol.FoundWord
import br.com.colman.palavramento.domain.protocol.LabelledWord
import br.com.colman.palavramento.domain.protocol.LeaderboardRow
import br.com.colman.palavramento.domain.protocol.ServerMessage
import br.com.colman.palavramento.domain.solver.WordTier
import br.com.colman.palavramento.domain.stats.RoundStats
import br.com.colman.palavramento.domain.submission.RejectionReason
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private fun sampleBoard() = List(16) { Tile("A", 1) }

private fun sampleRoundStart(
  alreadyFound: List<FoundWord> = emptyList(),
  runningScore: Int = 0,
  runningWords: Int = 0,
) =
  ServerMessage.RoundStart(
    roundId = "round-1",
    board = sampleBoard(),
    mutator = Mutator.NoMutator,
    themeTitle = "Grade padrao",
    themeSubtitle = "15 palavras comuns",
    maxScore = 4193,
    maxWords = 272,
    startsAt = 1_000,
    endsAt = 121_000,
    alreadyFound = alreadyFound,
    runningScore = runningScore,
    runningWords = runningWords,
  )

class MatchStateReducerTest : FunSpec({

  test("LobbyState on a fresh connection starts the Lobby state") {
    val state = MatchStateReducer.reduce(MatchUiState.Disconnected, ServerMessage.LobbyState(5_000, 3))
    state shouldBe MatchUiState.Lobby(5_000, 3)
  }

  test("RoundStart moves straight into InRound, regardless of the previous state") {
    val state = MatchStateReducer.reduce(MatchUiState.Lobby(0, 0), sampleRoundStart())
    state shouldBe MatchUiState.InRound(
      roundId = "round-1",
      board = sampleBoard(),
      mutator = Mutator.NoMutator,
      themeTitle = "Grade padrao",
      themeSubtitle = "15 palavras comuns",
      maxScore = 4193,
      maxWords = 272,
      startsAt = 1_000,
      endsAt = 121_000,
      foundWords = emptyList(),
      runningScore = 0,
      runningWords = 0,
    )
  }

  test("WordAccepted during a round appends the word and updates the running totals") {
    val inRound = MatchStateReducer.reduce(MatchUiState.Disconnected, sampleRoundStart())
    val accepted = ServerMessage.WordAccepted(
      "LIMO",
      17,
      runningScore = 17,
      runningWords = 1,
      path = listOf(0, 1, 2, 3)
    )

    val state = MatchStateReducer.reduce(inRound, accepted) as MatchUiState.InRound

    state.foundWords shouldBe listOf(FoundWord("LIMO", 17, listOf(0, 1, 2, 3)))
    state.runningScore shouldBe 17
    state.runningWords shouldBe 1
    state.lastFeedback shouldBe SubmissionFeedback.Accepted("LIMO", 17, listOf(0, 1, 2, 3))
  }

  test("WordRejected during a round records the rejection reason without touching found words") {
    val inRound = MatchStateReducer.reduce(MatchUiState.Disconnected, sampleRoundStart())
    val rejected = ServerMessage.WordRejected(RejectionReason.AlreadyFound, path = listOf(0, 1))

    val state = MatchStateReducer.reduce(inRound, rejected) as MatchUiState.InRound

    state.foundWords shouldBe emptyList()
    state.lastFeedback shouldBe SubmissionFeedback.Rejected(RejectionReason.AlreadyFound, listOf(0, 1))
  }

  test("RoundEnd moves into PostRound, carrying the max score/words and board from the round that just ended") {
    var state: MatchUiState = MatchStateReducer.reduce(MatchUiState.Disconnected, sampleRoundStart())
    state = MatchStateReducer.reduce(
      state,
      ServerMessage.WordAccepted("LIMO", 17, runningScore = 17, runningWords = 1, path = listOf(0, 1, 2, 3)),
    )

    val stats = RoundStats(
      points = 17,
      words = 1,
      secondsPerWord = 4.0,
      averageLength = 4.0,
      bonusPoints = 0,
      averagePoints = 17.0,
      xp = 3
    )
    val words = listOf(LabelledWord("LIMO", 17, WordTier.Common, listOf(0, 1, 2, 3), found = true))
    state = MatchStateReducer.reduce(state, ServerMessage.RoundEnd("round-1", stats, words))

    val postRound = state as MatchUiState.PostRound
    postRound.roundId shouldBe "round-1"
    postRound.stats shouldBe stats
    postRound.words shouldBe words
    postRound.maxScore shouldBe 4193
    postRound.maxWords shouldBe 272
    postRound.board shouldBe sampleBoard()
    postRound.leaderboard shouldBe null
  }

  test("Leaderboard after RoundEnd attaches to the existing PostRound state") {
    val stats = RoundStats(0, 0, 0.0, 0.0, 0, 0.0, 0)
    val postRound = MatchStateReducer.reduce(
      MatchStateReducer.reduce(MatchUiState.Disconnected, sampleRoundStart()),
      ServerMessage.RoundEnd("round-1", stats, emptyList()),
    )

    val leaderboardMessage = ServerMessage.Leaderboard(
      players = listOf(LeaderboardRow(1, "Ana", 100, 10)),
      self = LeaderboardRow(1, "Ana", 100, 10),
      percentile = 100,
      totalPlayers = 1,
    )
    val state = MatchStateReducer.reduce(postRound, leaderboardMessage) as MatchUiState.PostRound

    state.leaderboard shouldBe leaderboardMessage
  }

  test("LobbyState while showing results only updates the next-round countdown, keeping the results") {
    val stats = RoundStats(0, 0, 0.0, 0.0, 0, 0.0, 0)
    val postRound = MatchStateReducer.reduce(
      MatchStateReducer.reduce(MatchUiState.Disconnected, sampleRoundStart()),
      ServerMessage.RoundEnd("round-1", stats, emptyList()),
    ) as MatchUiState.PostRound

    val state = MatchStateReducer.reduce(postRound, ServerMessage.LobbyState(9_999, 7)) as MatchUiState.PostRound

    state.nextRoundStartsAt shouldBe 9_999
    state.playersWaiting shouldBe 7
    state.stats shouldBe stats
  }

  test("A reconnect RoundStart carrying alreadyFound restores exactly those words and totals") {
    val alreadyFound = listOf(FoundWord("CASA", 6, listOf(0, 1, 2, 3)), FoundWord("SOL", 4, listOf(4, 5, 6)))
    val reconnectRoundStart = sampleRoundStart(alreadyFound = alreadyFound, runningScore = 10, runningWords = 2)

    // Simulates a fresh connection receiving RoundStart mid-round, exactly like the very first join.
    val state = MatchStateReducer.reduce(MatchUiState.Disconnected, reconnectRoundStart) as MatchUiState.InRound

    state.foundWords shouldBe alreadyFound
    state.runningScore shouldBe 10
    state.runningWords shouldBe 2
    state.lastFeedback shouldBe null
  }

  test("ClockSyncResponse never changes the room state: it is consumed by the session, not the reducer") {
    val state = MatchUiState.Lobby(1, 2)
    MatchStateReducer.reduce(state, ServerMessage.ClockSyncResponse(clientSentAt = 1, serverTime = 2)) shouldBe state
  }
})
