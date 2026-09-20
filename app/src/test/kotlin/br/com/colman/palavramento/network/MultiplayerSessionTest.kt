// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.network

import android.util.Log
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.protocol.ClientMessage
import br.com.colman.palavramento.domain.protocol.FoundWord
import br.com.colman.palavramento.domain.protocol.ServerMessage
import br.com.colman.palavramento.domain.protocol.ValidWord
import br.com.colman.palavramento.domain.submission.RejectionReason
import br.com.colman.palavramento.state.MatchUiState
import br.com.colman.palavramento.state.SubmissionFeedback
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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

// C A / T S: a 2x2 board fully connected by adjacency, so every path of 2-4 indices is valid on it.
private fun catBoard() = listOf(Tile("C", 3), Tile("A", 1), Tile("T", 3), Tile("S", 1))

/** A round whose own solution (ADR 0014's `validWords`) is just "cat", scored 7 (3+1+3) via [0, 1, 2]. */
private fun sampleRoundStartWithValidWords(roundId: String = "round-1", endsAt: Long = 120_000) =
  ServerMessage.RoundStart(
    roundId = roundId,
    board = catBoard(),
    mutator = Mutator.NoMutator,
    themeTitle = "Grade padrao",
    themeSubtitle = "15 palavras comuns",
    maxScore = 7,
    maxWords = 1,
    startsAt = 0,
    endsAt = endsAt,
    validWords = listOf(ValidWord("CAT", "cat")),
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

  test("A locally valid word (ADR 0014) updates state instantly and is still sent to the server") {
    runTest {
      val transport = FakeMultiplayerTransport()
      var ticks = 0L
      val session = MultiplayerSession(transport, { "token" }, { ticks++ }, delay = {})
      val job = launch { session.run() }

      completeHandshake(transport)
      transport.push(sampleRoundStartWithValidWords())
      advanceUntilIdle()

      session.submitWord("round-1", listOf(0, 1, 2), clientTimestampMs = 1_000)
      advanceUntilIdle()

      val state = session.state.value as MatchUiState.InRound
      state.foundWords shouldBe listOf(FoundWord("cat", 7, listOf(0, 1, 2)))
      state.runningScore shouldBe 7
      state.runningWords shouldBe 1
      state.lastFeedback shouldBe SubmissionFeedback.Accepted("cat", 7, listOf(0, 1, 2))
      state.pendingPaths shouldBe setOf(listOf(0, 1, 2))
      transport.sent.filterIsInstance<ClientMessage.SubmitWord>().single().path shouldBe listOf(0, 1, 2)

      job.cancelAndJoin()
    }
  }

  test("A locally invalid word (ADR 0014) shows rejection feedback immediately and is never sent") {
    runTest {
      val transport = FakeMultiplayerTransport()
      var ticks = 0L
      val session = MultiplayerSession(transport, { "token" }, { ticks++ }, delay = {})
      val job = launch { session.run() }

      completeHandshake(transport)
      transport.push(sampleRoundStartWithValidWords())
      advanceUntilIdle()

      // C A S: not in this round's solution (only "cat" is).
      session.submitWord("round-1", listOf(0, 1, 3), clientTimestampMs = 1_000)
      advanceUntilIdle()

      val state = session.state.value as MatchUiState.InRound
      state.foundWords shouldBe emptyList()
      state.lastFeedback shouldBe SubmissionFeedback.Rejected(RejectionReason.NotAWord, listOf(0, 1, 3))
      transport.sent.none { it is ClientMessage.SubmitWord } shouldBe true

      job.cancelAndJoin()
    }
  }

  test("Empty validWords keeps today's behavior: no local verdict, straight to the server") {
    runTest {
      val transport = FakeMultiplayerTransport()
      var ticks = 0L
      val session = MultiplayerSession(transport, { "token" }, { ticks++ }, delay = {})
      val job = launch { session.run() }

      completeHandshake(transport)
      transport.push(sampleRoundStart(alreadyFound = emptyList()))
      advanceUntilIdle()

      session.submitWord("round-1", listOf(0, 1, 2, 3), clientTimestampMs = 1_000)
      advanceUntilIdle()

      val state = session.state.value as MatchUiState.InRound
      state.foundWords shouldBe emptyList()
      state.lastFeedback shouldBe null
      transport.sent.filterIsInstance<ClientMessage.SubmitWord>().single().path shouldBe listOf(0, 1, 2, 3)

      job.cancelAndJoin()
    }
  }

  test("After endsAt (synced server clock), no local verdict: the submission just goes to the server") {
    runTest {
      val transport = FakeMultiplayerTransport()
      var ticks = 0L
      val session = MultiplayerSession(transport, { "token" }, { ticks++ }, delay = {})
      val job = launch { session.run() }

      completeHandshake(transport)
      // endsAt = 0: the handshake's synced clock (serverTime = 500 in completeHandshake) is already past it.
      transport.push(sampleRoundStartWithValidWords(endsAt = 0))
      advanceUntilIdle()

      session.submitWord("round-1", listOf(0, 1, 2), clientTimestampMs = 1_000)
      advanceUntilIdle()

      val state = session.state.value as MatchUiState.InRound
      state.foundWords shouldBe emptyList()
      state.lastFeedback shouldBe null
      transport.sent.filterIsInstance<ClientMessage.SubmitWord>().single().path shouldBe listOf(0, 1, 2)

      job.cancelAndJoin()
    }
  }

  test("A server confirmation for a locally accepted word reconciles totals without a second accept") {
    runTest {
      val transport = FakeMultiplayerTransport()
      var ticks = 0L
      val session = MultiplayerSession(transport, { "token" }, { ticks++ }, delay = {})
      val job = launch { session.run() }

      completeHandshake(transport)
      transport.push(sampleRoundStartWithValidWords())
      advanceUntilIdle()

      session.submitWord("round-1", listOf(0, 1, 2), clientTimestampMs = 1_000)
      advanceUntilIdle()
      val afterLocalAccept = session.state.value as MatchUiState.InRound
      afterLocalAccept.pendingPaths shouldBe setOf(listOf(0, 1, 2))

      transport.push(ServerMessage.WordAccepted("cat", 7, runningScore = 7, runningWords = 1, path = listOf(0, 1, 2)))
      advanceUntilIdle()

      val state = session.state.value as MatchUiState.InRound
      state.foundWords shouldBe listOf(FoundWord("cat", 7, listOf(0, 1, 2)))
      state.runningScore shouldBe 7
      state.runningWords shouldBe 1
      state.pendingPaths shouldBe emptySet()
      // Same feedback value as right after the local accept: no second flash/sound/haptic.
      state.lastFeedback shouldBe afterLocalAccept.lastFeedback

      job.cancelAndJoin()
    }
  }

  test("A server rejection of a locally accepted word rolls it back") {
    mockkStatic(Log::class)
    every { Log.w(any(), any<String>()) } returns 0
    try {
      runTest {
        val transport = FakeMultiplayerTransport()
        var ticks = 0L
        val session = MultiplayerSession(transport, { "token" }, { ticks++ }, delay = {})
        val job = launch { session.run() }

        completeHandshake(transport)
        transport.push(sampleRoundStartWithValidWords())
        advanceUntilIdle()

        session.submitWord("round-1", listOf(0, 1, 2), clientTimestampMs = 1_000)
        advanceUntilIdle()

        // Rare (the equivalence property test in :domain is the argument this should not happen):
        // the server disagrees with the local accept.
        transport.push(ServerMessage.WordRejected(RejectionReason.NotAWord, path = listOf(0, 1, 2)))
        advanceUntilIdle()

        val state = session.state.value as MatchUiState.InRound
        state.foundWords shouldBe emptyList()
        state.runningScore shouldBe 0
        state.runningWords shouldBe 0
        state.pendingPaths shouldBe emptySet()
        state.lastFeedback shouldBe
          SubmissionFeedback.Rejected(RejectionReason.NotAWord, listOf(0, 1, 2), serial = 2)

        job.cancelAndJoin()
      }
    } finally {
      unmockkStatic(Log::class)
    }
  }

  test("A word accepted locally while disconnected is queued, then resent after reconnecting") {
    runTest {
      val transport = FakeMultiplayerTransport()
      var ticks = 0L
      val session = MultiplayerSession(transport, { "token" }, { ticks++ }, delay = {})
      val job = launch { session.run() }

      completeHandshake(transport)
      transport.push(sampleRoundStartWithValidWords())
      advanceUntilIdle()

      transport.dropConnection()
      advanceUntilIdle()
      session.connectionStatus.value shouldBe ConnectionStatus.Reconnecting

      // The local verdict does not require a live connection: applied right away, queued to send.
      session.submitWord("round-1", listOf(0, 1, 2), clientTimestampMs = 1_000)
      val afterLocalAccept = session.state.value as MatchUiState.InRound
      afterLocalAccept.foundWords shouldBe listOf(FoundWord("cat", 7, listOf(0, 1, 2)))
      afterLocalAccept.pendingPaths shouldBe setOf(listOf(0, 1, 2))
      transport.sent.none { it is ClientMessage.SubmitWord } shouldBe true

      completeHandshake(transport)
      // The server's own RoundStart never learned about this word (it was never sent before the drop).
      transport.push(sampleRoundStartWithValidWords())
      advanceUntilIdle()

      // Kept, not lost: PendingSubmissionQueue already resends anything queued for the round that is
      // still running (docs/adr/0008-polimento-do-app.md); ADR 0014 reuses that one mechanism instead
      // of inventing a second resend path.
      transport.sent.filterIsInstance<ClientMessage.SubmitWord>().single().path shouldBe listOf(0, 1, 2)
      val state = session.state.value as MatchUiState.InRound
      // RoundStart rebuilds InRound from the server's own alreadyFound (empty here), clearing
      // pendingPaths: the word reappears once the resend's WordAccepted confirms it.
      state.foundWords shouldBe emptyList()
      state.pendingPaths shouldBe emptySet()

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
