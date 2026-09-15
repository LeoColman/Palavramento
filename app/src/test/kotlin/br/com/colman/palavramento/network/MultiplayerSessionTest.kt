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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

private fun sampleRoundStart(alreadyFound: List<FoundWord>, roundId: String = "round-1") = ServerMessage.RoundStart(
  roundId = roundId,
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
      // Unstable-network handling (task brief 5, docs/adr/0008-polimento-do-app.md): the room screen
      // stays on InRound across the drop - nothing found so far disappears from view - while
      // connectionStatus is the separate signal a "Reconectando..." banner reads.
      session.connectionStatus.value shouldBe ConnectionStatus.Reconnecting
      (session.state.value as MatchUiState.InRound).foundWords shouldBe alreadyFound

      completeHandshake(transport)
      session.connectionStatus.value shouldBe ConnectionStatus.Connected
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

  test("A word submitted while disconnected is queued, then resent once reconnected into the same round") {
    runTest {
      val transport = FakeMultiplayerTransport()
      var ticks = 0L
      val session = MultiplayerSession(transport, { "token" }, { ticks++ }, delay = {})
      val job = launch { session.run() }

      completeHandshake(transport)
      val alreadyFound = listOf(FoundWord("CASA", 6, listOf(0, 1, 2, 3)))
      transport.push(sampleRoundStart(alreadyFound))
      advanceUntilIdle()

      transport.dropConnection()
      advanceUntilIdle()
      session.connectionStatus.value shouldBe ConnectionStatus.Reconnecting

      // Submitted while the socket is down: queued, nothing sent yet.
      session.submitWord("round-1", listOf(4, 5, 6), clientTimestampMs = 1_000)
      transport.sent.none { it is ClientMessage.SubmitWord } shouldBe true

      // Reconnect handshake, then the server confirms round-1 is still the round running.
      completeHandshake(transport)
      transport.push(sampleRoundStart(alreadyFound))
      advanceUntilIdle()

      transport.sent.filterIsInstance<ClientMessage.SubmitWord>().single().path shouldBe listOf(4, 5, 6)
      // Found words already restored, per the mid-round reconnect scenario above: this queued
      // resend never displaces them, even before the server's answer to it arrives.
      (session.state.value as MatchUiState.InRound).foundWords shouldBe alreadyFound

      job.cancelAndJoin()
    }
  }

  test("A submission queued for a round that has since ended is dropped, not resent") {
    runTest {
      val transport = FakeMultiplayerTransport()
      var ticks = 0L
      val session = MultiplayerSession(transport, { "token" }, { ticks++ }, delay = {})
      val job = launch { session.run() }

      completeHandshake(transport)
      transport.push(sampleRoundStart(emptyList(), roundId = "round-1"))
      advanceUntilIdle()

      transport.dropConnection()
      advanceUntilIdle()
      session.submitWord("round-1", listOf(0, 1, 2), clientTimestampMs = 1_000)

      // A new round already started by the time the client reconnects.
      completeHandshake(transport)
      transport.push(sampleRoundStart(emptyList(), roundId = "round-2"))
      advanceUntilIdle()

      transport.sent.none { it is ClientMessage.SubmitWord } shouldBe true
      (session.state.value as MatchUiState.InRound).roundId shouldBe "round-2"

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

  test("The clock survives a connection drop instead of resetting to null, until a fresh handshake replaces it") {
    runTest {
      val transport = FakeMultiplayerTransport()
      var ticks = 0L
      val session = MultiplayerSession(transport, { "token" }, { ticks++ }, delay = {})
      val job = launch { session.run() }

      completeHandshake(transport)
      val clockAfterFirstHandshake = session.clock.value
      clockAfterFirstHandshake.shouldNotBeNull()

      transport.dropConnection()
      advanceUntilIdle()
      // Orchestrator finding (task brief 4): the match countdown read 00:00 throughout
      // "Reconectando..." because this used to be nulled out on every drop; it must keep the last
      // synced clock instead, right up until a fresh handshake actually has a new one to offer.
      session.clock.value shouldBeSameInstanceAs clockAfterFirstHandshake

      completeHandshake(transport)
      session.clock.value.shouldNotBeNull()
      session.clock.value shouldNotBeSameInstanceAs clockAfterFirstHandshake

      job.cancelAndJoin()
    }
  }

  test("connectionAttempts counts consecutive failures and resets to 0 once connected") {
    runTest {
      val transport = FakeMultiplayerTransport()
      transport.connectFailure = IllegalStateException("network down")
      var ticks = 0L
      val session = MultiplayerSession(transport, { "token" }, { ticks++ }, delay = {})
      val job = launch { session.run() }

      session.connectionAttempts.value shouldBe 0
      advanceUntilIdle()
      // The one failed attempt (transport.connectFailure fires once, then clears itself).
      session.connectionAttempts.value shouldBe 1

      completeHandshake(transport)
      session.connectionAttempts.value shouldBe 0

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
