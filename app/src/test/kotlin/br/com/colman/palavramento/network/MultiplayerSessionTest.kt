// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.network

import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.protocol.ClientMessage
import br.com.colman.palavramento.domain.protocol.FoundWord
import br.com.colman.palavramento.domain.protocol.ServerMessage
import br.com.colman.palavramento.state.MatchUiState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

private fun sampleRoundStart(alreadyFound: List<FoundWord>) = ServerMessage.RoundStart(
  roundId = "round-1",
  board = List(16) { Tile("A", 1) },
  mutator = Mutator.NoMutator,
  themeTitle = "Grade padrao",
  themeSubtitle = "15 palavras comuns",
  maxScore = 4193,
  maxWords = 272,
  startsAt = 0,
  endsAt = 120_000,
  alreadyFound = alreadyFound,
  runningScore = alreadyFound.sumOf { it.score },
  runningWords = alreadyFound.size,
)

class MultiplayerSessionTest : FunSpec({

  test("The handshake sends every clock-sync sample, then JoinRoom carrying the access token") {
    runTest {
      val transport = FakeMultiplayerTransport()
      var ticks = 0L
      val session = MultiplayerSession(
        transport = transport,
        accessTokenProvider = { "token-abc" },
        elapsedRealtimeMs = { ticks++ },
        delay = {},
      )
      val job = launch { session.run() }

      repeat(3) {
        advanceUntilIdle()
        val lastSync = transport.sent.last() as ClientMessage.ClockSync
        transport.push(ServerMessage.ClockSyncResponse(lastSync.clientSentAt, serverTime = 500))
      }
      advanceUntilIdle()

      transport.sent.count { it is ClientMessage.ClockSync } shouldBe 3
      transport.sent.last() shouldBe ClientMessage.JoinRoom(sessionToken = "token-abc")
      transport.connectCount shouldBe 1

      job.cancelAndJoin()
    }
  }

  test("Non clock-sync messages fold into state through the reducer once the handshake is done") {
    runTest {
      val transport = FakeMultiplayerTransport()
      var ticks = 0L
      val session = MultiplayerSession(transport, { "token" }, { ticks++ }, delay = {})
      val job = launch { session.run() }

      completeHandshake(transport)

      val roundStart = sampleRoundStart(alreadyFound = emptyList())
      transport.push(roundStart)
      advanceUntilIdle()

      val state = session.state.value
      state.shouldBeInRoundWithRoundId("round-1")

      job.cancelAndJoin()
    }
  }

  test("On drop, the session reconnects, replays the handshake and JoinRoom, and restores alreadyFound") {
    runTest {
      val transport = FakeMultiplayerTransport()
      var ticks = 0L
      val session = MultiplayerSession(transport, { "token" }, { ticks++ }, delay = {})
      val job = launch { session.run() }

      completeHandshake(transport)
      transport.connectCount shouldBe 1
      transport.sent.count { it is ClientMessage.JoinRoom } shouldBe 1

      val alreadyFound = listOf(FoundWord("CASA", 6, listOf(0, 1, 2, 3)))
      transport.push(sampleRoundStart(alreadyFound))
      advanceUntilIdle()
      (session.state.value as MatchUiState.InRound).foundWords shouldBe alreadyFound

      transport.dropConnection()
      advanceUntilIdle()
      session.state.value shouldBe MatchUiState.Disconnected

      completeHandshake(transport)
      transport.connectCount shouldBe 2
      transport.sent.count { it is ClientMessage.JoinRoom } shouldBe 2

      // The server re-sends RoundStart with the words already found, and the client restores them
      // (dossier 5.3) exactly like the first time.
      transport.push(sampleRoundStart(alreadyFound))
      advanceUntilIdle()
      (session.state.value as MatchUiState.InRound).foundWords shouldBe alreadyFound

      job.cancelAndJoin()
    }
  }

  test("A connect() failure does not crash the session: it retries on the next loop turn") {
    runTest {
      val transport = FakeMultiplayerTransport()
      transport.connectFailure = IllegalStateException("network down")
      var ticks = 0L
      val session = MultiplayerSession(transport, { "token" }, { ticks++ }, delay = {})
      val job = launch { session.run() }

      advanceUntilIdle()
      completeHandshake(transport)

      transport.connectCount shouldBe 2
      transport.sent.last() shouldBe ClientMessage.JoinRoom(sessionToken = "token")

      job.cancelAndJoin()
    }
  }
})

/** Answers every pending `ClockSync` with a matching response until `JoinRoom` is sent. */
private suspend fun TestScope.completeHandshake(transport: FakeMultiplayerTransport) {
  repeat(3) {
    advanceUntilIdle()
    val lastSync = transport.sent.last() as ClientMessage.ClockSync
    transport.push(ServerMessage.ClockSyncResponse(lastSync.clientSentAt, serverTime = 500))
  }
  advanceUntilIdle()
}

private fun MatchUiState.shouldBeInRoundWithRoundId(roundId: String) {
  val inRound = this as MatchUiState.InRound
  inRound.roundId shouldBe roundId
}
