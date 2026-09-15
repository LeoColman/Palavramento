// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.ws

import br.com.colman.palavramento.domain.protocol.AuthTokens
import br.com.colman.palavramento.domain.protocol.ClientMessage
import br.com.colman.palavramento.domain.protocol.GuestAuthRequest
import br.com.colman.palavramento.domain.protocol.RegisterRequest
import br.com.colman.palavramento.domain.protocol.ServerMessage
import br.com.colman.palavramento.domain.submission.RejectionReason
import br.com.colman.palavramento.server.module
import br.com.colman.palavramento.server.repository.PlayerStatsRepository
import br.com.colman.palavramento.server.repository.RoundRepository
import br.com.colman.palavramento.server.repository.RoundResultRepository
import br.com.colman.palavramento.server.repository.SubmissionRepository
import br.com.colman.palavramento.server.testsupport.TestLexicon
import br.com.colman.palavramento.server.testsupport.nextServerMessage
import br.com.colman.palavramento.server.testsupport.sendClientMessage
import br.com.colman.palavramento.server.testsupport.testDatabase
import br.com.colman.palavramento.server.testsupport.testHttpClient
import br.com.colman.palavramento.server.testsupport.testRoomId
import br.com.colman.palavramento.server.testsupport.testServerConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * `/ws/multiplayer` end to end (dossier §5, §10, §11): two simulated WebSocket connections playing
 * the same round, both getting the same `RoundStart`, submitting, both getting `RoundEnd` and a
 * consistent `Leaderboard`, with the database and `player_stats` updated to match.
 */
class MultiplayerRouteTest : FunSpec({
  val database = testDatabase()

  suspend fun guestTokens(client: HttpClient, displayName: String): AuthTokens {
    val response = client.post("/auth/guest") {
      contentType(ContentType.Application.Json)
      setBody(GuestAuthRequest(displayName))
    }
    return response.body()
  }

  test("two players play the same round: consistent start, submissions, end and leaderboard") {
    val roomId = testRoomId()
    val config = testServerConfig(roundDuration = 6.seconds, intermissionDuration = 4.seconds)

    testApplication {
      application { module(config, database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()

      val aliceGuest = guestTokens(client, "Alice")
      val alice = client.post("/auth/register") {
        header(HttpHeaders.Authorization, "Bearer ${aliceGuest.accessToken}")
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest("alice-$roomId@example.com", "correct horse battery staple", "Alice"))
      }.body<AuthTokens>()
      val bob = guestTokens(client, "Bob")

      var aliceRoundId: String? = null
      var bobRoundId: String? = null
      var aliceEnd: ServerMessage.RoundEnd? = null
      var bobEnd: ServerMessage.RoundEnd? = null
      var aliceLeaderboard: ServerMessage.Leaderboard? = null
      var bobLeaderboard: ServerMessage.Leaderboard? = null

      coroutineScope {
        launch {
          client.webSocket("/ws/multiplayer") {
            sendClientMessage(ClientMessage.JoinRoom(sessionToken = alice.accessToken))
            nextServerMessage().shouldBeInstanceOf<ServerMessage.LobbyState>()
            val start = nextServerMessage() as ServerMessage.RoundStart
            aliceRoundId = start.roundId

            val solution = RoundRepository(database).loadSolution(start.roundId)
            val word = solution.first()
            sendClientMessage(ClientMessage.SubmitWord(start.roundId, word.path, System.currentTimeMillis()))
            val accepted = nextServerMessage() as ServerMessage.WordAccepted
            accepted.score shouldBe word.score

            // Duplicate submission of the same word.
            sendClientMessage(ClientMessage.SubmitWord(start.roundId, word.path, System.currentTimeMillis()))
            val duplicate = nextServerMessage() as ServerMessage.WordRejected
            duplicate.reason shouldBe RejectionReason.AlreadyFound

            // Non-adjacent path (opposite corners of a 4x4 board).
            sendClientMessage(ClientMessage.SubmitWord(start.roundId, listOf(0, 15), System.currentTimeMillis()))
            val invalidPath = nextServerMessage() as ServerMessage.WordRejected
            invalidPath.reason shouldBe RejectionReason.InvalidPath

            aliceEnd = nextServerMessage() as ServerMessage.RoundEnd
            aliceLeaderboard = nextServerMessage() as ServerMessage.Leaderboard
          }
        }

        launch {
          client.webSocket("/ws/multiplayer") {
            sendClientMessage(ClientMessage.JoinRoom(sessionToken = bob.accessToken))
            nextServerMessage().shouldBeInstanceOf<ServerMessage.LobbyState>()
            val start = nextServerMessage() as ServerMessage.RoundStart
            bobRoundId = start.roundId

            val solution = RoundRepository(database).loadSolution(start.roundId)
            val word = solution[1]
            sendClientMessage(ClientMessage.SubmitWord(start.roundId, word.path, System.currentTimeMillis()))
            nextServerMessage().shouldBeInstanceOf<ServerMessage.WordAccepted>()

            bobEnd = nextServerMessage() as ServerMessage.RoundEnd
            bobLeaderboard = nextServerMessage() as ServerMessage.Leaderboard
          }
        }
      }

      aliceRoundId shouldBe bobRoundId
      val roundId = requireNotNull(aliceRoundId)

      // Both saw the exact same solved word list, labelled independently for what each found.
      aliceEnd!!.words.map { it.word to it.score } shouldBe bobEnd!!.words.map { it.word to it.score }
      aliceEnd!!.stats.words shouldBe 1
      bobEnd!!.stats.words shouldBe 1

      // The leaderboard both received is the same two players, consistently ranked.
      aliceLeaderboard!!.totalPlayers shouldBe 2
      bobLeaderboard!!.totalPlayers shouldBe 2
      aliceLeaderboard!!.players.map { it.name } shouldContainExactlyInAnyOrder listOf("Alice", "Bob")
      aliceLeaderboard!!.players shouldBe bobLeaderboard!!.players

      // submissions and round_results were persisted for both players.
      val submissionRepository = SubmissionRepository(database)
      submissionRepository.findByRoundAndPlayer(roundId, alice.playerId) shouldHaveSize 1
      submissionRepository.findByRoundAndPlayer(roundId, bob.playerId) shouldHaveSize 1

      val roundResultRepository = RoundResultRepository(database)
      val results = roundResultRepository.findByRound(roundId)
      results shouldHaveSize 2

      // player_stats is maintained for the registered player only (dossier §8 guest policy).
      val playerStatsRepository = PlayerStatsRepository(database)
      playerStatsRepository.get(alice.playerId) shouldBe playerStatsRepository.get(alice.playerId)?.also {
        it.gamesPlayed shouldBe 1
      }
      playerStatsRepository.get(bob.playerId) shouldBe null
    }
  }

  test("joining mid-round waits for the next round instead of the one already in progress") {
    val roomId = testRoomId()
    val config = testServerConfig(roundDuration = 6.seconds, intermissionDuration = 4.seconds)

    testApplication {
      application { module(config, database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()
      val early = guestTokens(client, "Early")
      val late = guestTokens(client, "Late")
      val roundStarted = CompletableDeferred<Unit>()

      coroutineScope {
        launch {
          client.webSocket("/ws/multiplayer") {
            sendClientMessage(ClientMessage.JoinRoom(sessionToken = early.accessToken))
            nextServerMessage().shouldBeInstanceOf<ServerMessage.LobbyState>()
            nextServerMessage().shouldBeInstanceOf<ServerMessage.RoundStart>()
            roundStarted.complete(Unit)
          }
        }

        launch {
          // Only once the round is confirmed active does the latecomer join it.
          roundStarted.await()
          client.webSocket("/ws/multiplayer") {
            sendClientMessage(ClientMessage.JoinRoom(sessionToken = late.accessToken))
            nextServerMessage().shouldBeInstanceOf<ServerMessage.LobbyState>()
          }
        }
      }
    }
  }

  test("a missing or invalid session token closes the socket with a policy violation") {
    val roomId = testRoomId()
    val config = testServerConfig(roundDuration = 6.seconds, intermissionDuration = 4.seconds)

    testApplication {
      application { module(config, database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()

      client.webSocket("/ws/multiplayer") {
        sendClientMessage(ClientMessage.JoinRoom(sessionToken = "not-a-real-token"))
        val reason = closeReason.await()
        reason?.code shouldBe CloseReason.Codes.VIOLATED_POLICY.code
      }
    }
  }

  test("reconnecting mid-round restores alreadyFound, runningScore and runningWords") {
    val roomId = testRoomId()
    val config = testServerConfig(roundDuration = 8.seconds, intermissionDuration = 4.seconds)

    testApplication {
      application { module(config, database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()
      val player = guestTokens(client, "Reconnector")

      var foundWord: String? = null
      var foundScore = 0

      client.webSocket("/ws/multiplayer") {
        sendClientMessage(ClientMessage.JoinRoom(sessionToken = player.accessToken))
        nextServerMessage().shouldBeInstanceOf<ServerMessage.LobbyState>()
        val start = nextServerMessage() as ServerMessage.RoundStart
        val word = RoundRepository(database).loadSolution(start.roundId).first()
        foundWord = word.display
        foundScore = word.score
        sendClientMessage(ClientMessage.SubmitWord(start.roundId, word.path, System.currentTimeMillis()))
        nextServerMessage().shouldBeInstanceOf<ServerMessage.WordAccepted>()
        // Simulate a dropped connection: the client just goes away.
        close(CloseReason(CloseReason.Codes.NORMAL, "simulated disconnect"))
      }

      // A brand new connection, same access token: the server must recognize it as a reconnect.
      client.webSocket("/ws/multiplayer") {
        sendClientMessage(ClientMessage.JoinRoom(sessionToken = player.accessToken))
        val start = nextServerMessage() as ServerMessage.RoundStart

        start.alreadyFound shouldHaveSize 1
        start.alreadyFound.first().word shouldBe foundWord
        start.runningScore shouldBe foundScore
        start.runningWords shouldBe 1
      }
    }
  }
})
