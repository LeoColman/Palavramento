// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import br.com.colman.palavramento.audio.FakeGameAudio
import br.com.colman.palavramento.audio.SoundEffect
import br.com.colman.palavramento.data.SyncService
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.protocol.ClientMessage
import br.com.colman.palavramento.domain.protocol.LeaderboardRow
import br.com.colman.palavramento.domain.protocol.ServerMessage
import br.com.colman.palavramento.domain.stats.RoundStats
import br.com.colman.palavramento.domain.submission.RejectionReason
import br.com.colman.palavramento.network.ClockSyncSettings
import br.com.colman.palavramento.network.FakeMultiplayerTransport
import br.com.colman.palavramento.network.MultiplayerSession
import br.com.colman.palavramento.settings.FakeSettingsRepository
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

private fun sampleLeaderboardRow() = LeaderboardRow(1, "Ana", 73, 6)

/**
 * A fresh [RoomViewModel] over an in-memory [FakeMultiplayerTransport]/[FakeGameAudio]/
 * [FakeSettingsRepository], `RoomViewModel.init` starts [MultiplayerSession.run] itself on
 * `viewModelScope`, so these tests must not also launch it - [UnconfinedTestDispatcher] as
 * `Dispatchers.Main` is what lets that internally-started loop actually progress inside a plain
 * `runTest`.
 */
private class Fixture(musicEnabled: Boolean = true, effectsEnabled: Boolean = true) {
  val transport = FakeMultiplayerTransport()
  val gameAudio = FakeGameAudio()
  val settings = FakeSettingsRepository(musicEnabled = musicEnabled, effectsEnabled = effectsEnabled)
  val syncService: SyncService = mockk(relaxed = true)
  private var ticks = 0L
  val session = MultiplayerSession(
    transport,
    { "token" },
    { ticks++ },
    delay = {},
    clockSyncSettings = SemResync,
  )
  val viewModel = RoomViewModel(session, syncService, gameAudio, settings)
}

/** Re-sync off, same reason as in MultiplayerSessionTest: virtual time makes its wait instant. */
private val SemResync = ClockSyncSettings(resyncIntervalMs = 0)

class RoomViewModelTest : FunSpec({

  // Installed for the whole spec and deliberately never reset: a view model started by one test can
  // still be cancelling while the next one runs (RoomViewModel's session loop never ends on its own),
  // and resetting Main out from under it dispatches that cancellation into a Main dispatcher that no
  // longer exists, which on the JVM fails as "Looper not mocked". There is no real Main to restore.
  beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }

  test("a fresh RoundEnd triggers exactly one cache sync, not a repeat for the same round") {
    runTest {
      val transport = FakeMultiplayerTransport()
      var ticks = 0L
      val session = MultiplayerSession(
        transport,
        { "token" },
        { ticks++ },
        delay = {},
        clockSyncSettings = SemResync,
      )
      val syncService = mockk<SyncService>()
      coEvery { syncService.sync() } returns true

      val viewModel = RoomViewModel(session, syncService, FakeGameAudio(), FakeSettingsRepository())
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
      val session = MultiplayerSession(
        transport,
        { "token" },
        { ticks++ },
        delay = {},
        clockSyncSettings = SemResync,
      )
      val syncService = mockk<SyncService>()
      coEvery { syncService.sync() } returns true

      val viewModel = RoomViewModel(session, syncService, FakeGameAudio(), FakeSettingsRepository())
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

  test("entering a round starts the music exactly once") {
    runTest {
      val fixture = Fixture()
      completeHandshake(fixture.transport)

      fixture.transport.push(sampleRoundStart("round-1"))
      advanceUntilIdle()

      fixture.gameAudio.startedRoundIds shouldBe listOf("round-1")
      fixture.viewModel.leaveRoom()
    }
  }

  test("a reconnect RoundStart for the same round does not restart the track from zero") {
    runTest {
      val fixture = Fixture()
      completeHandshake(fixture.transport)

      fixture.transport.push(sampleRoundStart("round-1"))
      advanceUntilIdle()
      // Simulates a reconnect: the server resends RoundStart for the round already in progress.
      fixture.transport.push(sampleRoundStart("round-1"))
      advanceUntilIdle()

      fixture.gameAudio.startedRoundIds shouldBe listOf("round-1")
      fixture.viewModel.leaveRoom()
    }
  }

  test("a new round after another one restarts the music for the new round") {
    runTest {
      val fixture = Fixture()
      completeHandshake(fixture.transport)

      fixture.transport.push(sampleRoundStart("round-1"))
      advanceUntilIdle()
      fixture.transport.push(ServerMessage.RoundEnd("round-1", sampleStats(), emptyList()))
      advanceUntilIdle()
      fixture.transport.push(sampleRoundStart("round-2"))
      advanceUntilIdle()

      fixture.gameAudio.startedRoundIds shouldBe listOf("round-1", "round-2")
      fixture.viewModel.leaveRoom()
    }
  }

  test("RoundEnd stops the music") {
    runTest {
      val fixture = Fixture()
      completeHandshake(fixture.transport)

      fixture.transport.push(sampleRoundStart("round-1"))
      advanceUntilIdle()
      fixture.transport.push(ServerMessage.RoundEnd("round-1", sampleStats(), emptyList()))
      advanceUntilIdle()

      fixture.gameAudio.stopCount shouldBe 1
      fixture.viewModel.leaveRoom()
    }
  }

  test("leaving the room stops the music") {
    runTest {
      val fixture = Fixture()
      completeHandshake(fixture.transport)
      fixture.transport.push(sampleRoundStart("round-1"))
      advanceUntilIdle()

      fixture.viewModel.leaveRoom()

      fixture.gameAudio.stopCount shouldBe 1
    }
  }

  test("pausing stops the music, resuming with the same round in progress restarts it") {
    runTest {
      val fixture = Fixture()
      completeHandshake(fixture.transport)
      fixture.transport.push(sampleRoundStart("round-1"))
      advanceUntilIdle()

      fixture.viewModel.pause()
      fixture.gameAudio.stopCount shouldBe 1

      fixture.viewModel.resume()
      advanceUntilIdle()

      fixture.gameAudio.startedRoundIds shouldBe listOf("round-1", "round-1")
      fixture.viewModel.leaveRoom()
    }
  }

  test("music disabled in settings means startMusic is never called") {
    runTest {
      val fixture = Fixture(musicEnabled = false)
      completeHandshake(fixture.transport)

      fixture.transport.push(sampleRoundStart("round-1"))
      advanceUntilIdle()

      fixture.gameAudio.startedRoundIds shouldBe emptyList()
      fixture.viewModel.leaveRoom()
    }
  }

  test("an accepted word plays the Accepted effect") {
    runTest {
      val fixture = Fixture()
      completeHandshake(fixture.transport)
      fixture.transport.push(sampleRoundStart("round-1"))
      advanceUntilIdle()

      fixture.transport.push(
        ServerMessage.WordAccepted("CASA", 6, runningScore = 6, runningWords = 1, path = listOf(0, 1, 2, 3))
      )
      advanceUntilIdle()

      fixture.gameAudio.playedEffects shouldBe listOf(SoundEffect.Accepted)
      fixture.viewModel.leaveRoom()
    }
  }

  test("a rejected word already found plays the AlreadyFound effect, not the generic rejection one") {
    runTest {
      val fixture = Fixture()
      completeHandshake(fixture.transport)
      fixture.transport.push(sampleRoundStart("round-1"))
      advanceUntilIdle()

      fixture.transport.push(ServerMessage.WordRejected(RejectionReason.AlreadyFound, path = listOf(0, 1)))
      advanceUntilIdle()

      fixture.gameAudio.playedEffects shouldBe listOf(SoundEffect.AlreadyFound)
      fixture.viewModel.leaveRoom()
    }
  }

  test("effects disabled in settings means play is never called, even on a real rejection") {
    runTest {
      val fixture = Fixture(effectsEnabled = false)
      completeHandshake(fixture.transport)
      fixture.transport.push(sampleRoundStart("round-1"))
      advanceUntilIdle()

      fixture.transport.push(ServerMessage.WordRejected(RejectionReason.NotAWord, path = listOf(0, 1)))
      advanceUntilIdle()

      fixture.gameAudio.playedEffects shouldBe emptyList()
      fixture.viewModel.leaveRoom()
    }
  }

  test("updateMusicSpeed skips the call to GameAudio when the resulting speed has not changed") {
    runTest {
      val fixture = Fixture()

      fixture.viewModel.updateMusicSpeed(60_000)
      fixture.viewModel.updateMusicSpeed(59_000)
      fixture.viewModel.updateMusicSpeed(45_000)

      fixture.gameAudio.remainingUpdates shouldBe listOf(60_000L)
      fixture.viewModel.leaveRoom()
    }
  }

  test("updateMusicSpeed forwards again once the speed actually changes") {
    runTest {
      val fixture = Fixture()

      fixture.viewModel.updateMusicSpeed(60_000)
      fixture.viewModel.updateMusicSpeed(10_000)

      fixture.gameAudio.remainingUpdates shouldBe listOf(60_000L, 10_000L)
      fixture.viewModel.leaveRoom()
    }
  }
})
