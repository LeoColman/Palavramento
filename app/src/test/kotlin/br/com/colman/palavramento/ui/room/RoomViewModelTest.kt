// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import br.com.colman.palavramento.data.SyncService
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.protocol.ClientMessage
import br.com.colman.palavramento.domain.protocol.LeaderboardRow
import br.com.colman.palavramento.domain.protocol.ServerMessage
import br.com.colman.palavramento.domain.stats.RoundStats
import br.com.colman.palavramento.network.FakeMultiplayerTransport
import br.com.colman.palavramento.network.MultiplayerSession
import br.com.colman.palavramento.state.MatchUiState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private fun sampleStats() = RoundStats(
  points = 73,
  words = 6,
  secondsPerWord = 12.2,
  averageLength = 3.5,
  bonusPoints = 0,
  averagePoints = 12.2,
  xp = 14,
)

private fun sampleRoundStart(roundId: String) = ServerMessage.RoundStart(
  roundId = roundId,
  board = emptyList(),
  mutator = Mutator.NoMutator,
  themeTitle = "Grade padrao",
  themeSubtitle = "15 palavras comuns",
  maxScore = 1,
  maxWords = 1,
  startsAt = 0,
  endsAt = 1,
)

/** Answers every pending `ClockSync` until `JoinRoom` is sent (mirrors `MultiplayerSessionTest`'s own helper). */
private suspend fun TestScope.completeHandshake(transport: FakeMultiplayerTransport) {
  repeat(3) {
    advanceUntilIdle()
    val lastSync = transport.sent.last() as ClientMessage.ClockSync
    transport.push(ServerMessage.ClockSyncResponse(lastSync.clientSentAt, serverTime = 500))
  }
  advanceUntilIdle()
}

/**
 * [RoomViewModel]'s sync trigger (task brief 2: "after each RoundEnd... refreshes the local
 * cache"). `RoomViewModel.init` starts [MultiplayerSession.run] itself on `viewModelScope`, so
 * these tests must not also launch it - [UnconfinedTestDispatcher] as `Dispatchers.Main` is what
 * lets that internally-started loop actually progress inside a plain `runTest`.
 */
class RoomViewModelTest : FunSpec({

  beforeTest { Dispatchers.setMain(UnconfinedTestDispatcher()) }
  afterTest { Dispatchers.resetMain() }

  test("a fresh RoundEnd triggers exactly one cache sync, not a repeat for the same round") {
    runTest {
      val transport = FakeMultiplayerTransport()
      var ticks = 0L
      val session = MultiplayerSession(transport, { "token" }, { ticks++ }, delay = {})
      val syncService = mockk<SyncService>()
      coEvery { syncService.sync() } returns true

      val viewModel = RoomViewModel(session, syncService)
      completeHandshake(transport)

      transport.push(ServerMessage.RoundEnd("round-1", sampleStats(), emptyList()))
      advanceUntilIdle()
      (viewModel.state.value as MatchUiState.PostRound).roundId shouldBe "round-1"

      // A Leaderboard update for the *same* round only copies the existing PostRound in place (see
      // MatchStateReducer.onLeaderboard); it must not trigger a second sync.
      transport.push(
        ServerMessage.Leaderboard(emptyList(), self = sampleLeaderboardRow(), percentile = 50, totalPlayers = 1)
      )
      advanceUntilIdle()

      coVerify(exactly = 1) { syncService.sync() }

      viewModel.leaveRoom()
    }
  }

  test("a second, different RoundEnd triggers a second sync") {
    runTest {
      val transport = FakeMultiplayerTransport()
      var ticks = 0L
      val session = MultiplayerSession(transport, { "token" }, { ticks++ }, delay = {})
      val syncService = mockk<SyncService>()
      coEvery { syncService.sync() } returns true

      val viewModel = RoomViewModel(session, syncService)
      completeHandshake(transport)

      transport.push(ServerMessage.RoundEnd("round-1", sampleStats(), emptyList()))
      advanceUntilIdle()
      transport.push(sampleRoundStart("round-2"))
      advanceUntilIdle()
      transport.push(ServerMessage.RoundEnd("round-2", sampleStats(), emptyList()))
      advanceUntilIdle()

      coVerify(exactly = 2) { syncService.sync() }

      viewModel.leaveRoom()
    }
  }
})

private fun sampleLeaderboardRow() = LeaderboardRow(1, "Ana", 73, 6)
