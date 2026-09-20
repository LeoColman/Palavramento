// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.rest

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.protocol.AuthTokens
import br.com.colman.palavramento.domain.protocol.ClientMessage
import br.com.colman.palavramento.domain.protocol.GuestAuthRequest
import br.com.colman.palavramento.domain.protocol.LifetimeStats
import br.com.colman.palavramento.domain.protocol.LoginRequest
import br.com.colman.palavramento.domain.protocol.PlayerProfile
import br.com.colman.palavramento.domain.protocol.RegisterRequest
import br.com.colman.palavramento.domain.protocol.RoundHistoryEntry
import br.com.colman.palavramento.domain.protocol.ServerMessage
import br.com.colman.palavramento.domain.solver.SolvedWord
import br.com.colman.palavramento.domain.solver.WordTier
import br.com.colman.palavramento.server.auth.TokenHasher
import br.com.colman.palavramento.server.module
import br.com.colman.palavramento.server.repository.PlayerRepository
import br.com.colman.palavramento.server.repository.PlayerStatsRepository
import br.com.colman.palavramento.server.repository.PlayerStatsRow
import br.com.colman.palavramento.server.repository.RefreshTokenRepository
import br.com.colman.palavramento.server.repository.RoundRepository
import br.com.colman.palavramento.server.repository.RoundResultRepository
import br.com.colman.palavramento.server.repository.RoundResultRow
import br.com.colman.palavramento.server.repository.SubmissionRepository
import br.com.colman.palavramento.server.round.AcceptedSubmission
import br.com.colman.palavramento.server.round.RoundRecord
import br.com.colman.palavramento.server.round.RoundStatus
import br.com.colman.palavramento.server.testsupport.TestLexicon
import br.com.colman.palavramento.server.testsupport.insertFakeFinishedRound
import br.com.colman.palavramento.server.testsupport.nextServerMessage
import br.com.colman.palavramento.server.testsupport.sendClientMessage
import br.com.colman.palavramento.server.testsupport.testDatabase
import br.com.colman.palavramento.server.testsupport.testHttpClient
import br.com.colman.palavramento.server.testsupport.testRoomId
import br.com.colman.palavramento.server.testsupport.testServerConfig
import br.com.colman.palavramento.server.ws.CloseCodes
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/** `/players/me*` (dossier §6.1/§6.3/§6.4, `Rest.kt`). */
class PlayerRoutesTest : FunSpec({
  val database = testDatabase()

  test("players/me returns the guest's profile at level 1 with zero XP and a 100 XP target") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()
      val guest: AuthTokens = client.post("/auth/guest") {
        contentType(ContentType.Application.Json)
        setBody(GuestAuthRequest("Curious"))
      }.body()

      val response = client.get("/players/me") { header(HttpHeaders.Authorization, "Bearer ${guest.accessToken}") }
      response.status shouldBe HttpStatusCode.OK
      val profile: PlayerProfile = response.body()

      profile.playerId shouldBe guest.playerId
      profile.isGuest shouldBe true
      // Dossier 9's curve is 0-indexed math (xpForLevel(0) == 0); the lobby header (dossier 6.1)
      // shows a 1-indexed level, so a fresh player is "Nivel 1" with 100 XP to the next one, not
      // "Nivel 0" (see the comment in PlayerRoutes.kt and ADR 0009).
      profile.level shouldBe 1
      profile.totalXp shouldBe 0L
      profile.xpForNextLevel shouldBe 100L
    }
  }

  test("players/me reports a registered player's real totalXp when a stats row exists") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()
      val guest: AuthTokens = client.post("/auth/guest") {
        contentType(ContentType.Application.Json)
        setBody(GuestAuthRequest("XpPlayer"))
      }.body()
      val registered: AuthTokens = client.post("/auth/register") {
        header(HttpHeaders.Authorization, "Bearer ${guest.accessToken}")
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest("xp-$roomId@example.com", "correct horse battery staple", "XpPlayer"))
      }.body()

      PlayerStatsRepository(database).replace(
        PlayerStatsRow(
          playerId = registered.playerId,
          totalScore = 10,
          totalWords = 1,
          bestGameScore = 10,
          bestWord = "casa",
          bestWordScore = 10,
          gamesPlayed = 1,
          gamesCompleted = 1,
          bestRank = 1,
          totalXp = 250,
        ),
      )

      val profile: PlayerProfile = client.get(
        "/players/me"
      ) { header(HttpHeaders.Authorization, "Bearer ${registered.accessToken}") }.body()

      profile.totalXp shouldBe 250L
    }
  }

  test("players/me defaults totalXp to zero for a registered player with no stats row yet") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()
      val guest: AuthTokens = client.post("/auth/guest") {
        contentType(ContentType.Application.Json)
        setBody(GuestAuthRequest("FreshRegistered"))
      }.body()
      val registered: AuthTokens = client.post("/auth/register") {
        header(HttpHeaders.Authorization, "Bearer ${guest.accessToken}")
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest("fresh-$roomId@example.com", "correct horse battery staple", "FreshRegistered"))
      }.body()

      val profile: PlayerProfile = client.get(
        "/players/me"
      ) { header(HttpHeaders.Authorization, "Bearer ${registered.accessToken}") }.body()

      profile.totalXp shouldBe 0L
    }
  }

  test("players/me/stats answers 403 for a guest") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()
      val guest: AuthTokens = client.post("/auth/guest") {
        contentType(ContentType.Application.Json)
        setBody(GuestAuthRequest("Curious"))
      }.body()

      val response = client.get(
        "/players/me/stats"
      ) { header(HttpHeaders.Authorization, "Bearer ${guest.accessToken}") }
      response.status shouldBe HttpStatusCode.Forbidden
      response.body<ErrorBody>().error shouldBe "Guests have no lifetime stats (dossier 8)"
    }
  }

  test("players/me answers 404 once the player's account is gone") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()
      client.post("/auth/register") {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest("owner-$roomId@example.com", "correct horse battery staple", "Owner"))
      }.status shouldBe HttpStatusCode.OK
      val guest: AuthTokens = client.post("/auth/guest") {
        contentType(ContentType.Application.Json)
        setBody(GuestAuthRequest("Ghost"))
      }.body()

      // Login-migration (ADR 0007) deletes the guest's player row once nothing else references it;
      // the guest's own access token is a signed JWT and still verifies fine after that.
      client.post("/auth/login") {
        header(HttpHeaders.Authorization, "Bearer ${guest.accessToken}")
        contentType(ContentType.Application.Json)
        setBody(LoginRequest("owner-$roomId@example.com", "correct horse battery staple"))
      }.status shouldBe HttpStatusCode.OK

      val response = client.get("/players/me") { header(HttpHeaders.Authorization, "Bearer ${guest.accessToken}") }
      response.status shouldBe HttpStatusCode.NotFound
    }
  }

  test("players/me/stats reflects lifetime aggregates for a registered player") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()
      val guest: AuthTokens = client.post("/auth/guest") {
        contentType(ContentType.Application.Json)
        setBody(GuestAuthRequest("StatsPlayer"))
      }.body()
      val registered: AuthTokens = client.post("/auth/register") {
        header(HttpHeaders.Authorization, "Bearer ${guest.accessToken}")
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest("stats-$roomId@example.com", "correct horse battery staple", "StatsPlayer"))
      }.body()

      // player_stats is what /players/me/stats actually reads (dossier §8): populated here the same
      // way RoundFinalizer would at round end, without needing to play a full live round.
      val playerStatsRepository = PlayerStatsRepository(database)
      playerStatsRepository.replace(
        PlayerStatsRow(
          playerId = registered.playerId,
          totalScore = 150,
          totalWords = 15,
          bestGameScore = 100,
          bestWord = "casa",
          bestWordScore = 12,
          gamesPlayed = 2,
          gamesCompleted = 2,
          bestRank = 1,
          totalXp = 35,
        ),
      )

      val response = client.get(
        "/players/me/stats"
      ) { header(HttpHeaders.Authorization, "Bearer ${registered.accessToken}") }
      response.status shouldBe HttpStatusCode.OK
      val stats: LifetimeStats = response.body()

      stats.totalScore shouldBe 150L
      stats.totalWords shouldBe 15L
      stats.bestGameScore shouldBe 100
      stats.bestWord shouldBe "casa"
      stats.gamesPlayed shouldBe 2
      stats.gamesCompleted shouldBe 2
      stats.bestRank shouldBe 1
      stats.averageScore shouldBe 75.0
      stats.averageWords shouldBe 7.5
      stats.averagePointsPerWord shouldBe 10.0
    }
  }

  test("players/me/stats defaults to all-zero aggregates for a registered player with no rounds yet") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()
      val registered: AuthTokens = client.post("/auth/register") {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest("freshstats-$roomId@example.com", "correct horse battery staple", "Fresh"))
      }.body()

      // No player_stats row was ever written for this player: PlayerStatsRow?.toLifetimeStats()'s
      // null branch, with every average guarded against a division by zero games/words played.
      val response = client.get(
        "/players/me/stats"
      ) { header(HttpHeaders.Authorization, "Bearer ${registered.accessToken}") }
      response.status shouldBe HttpStatusCode.OK
      val stats: LifetimeStats = response.body()

      stats.totalScore shouldBe 0L
      stats.totalWords shouldBe 0L
      stats.bestGameScore shouldBe 0
      stats.bestWord shouldBe null
      stats.bestWordScore shouldBe 0
      stats.gamesPlayed shouldBe 0
      stats.gamesCompleted shouldBe 0
      stats.bestRank shouldBe null
      stats.averageScore shouldBe 0.0
      stats.averageWords shouldBe 0.0
      stats.averagePointsPerWord shouldBe 0.0
    }
  }

  test("players/me/stats computes averages right at the zero-games / zero-words boundary") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()

      val zeroGamesGuest: AuthTokens = client.post("/auth/guest") {
        contentType(ContentType.Application.Json)
        setBody(GuestAuthRequest("ZeroGames"))
      }.body()
      val zeroGames: AuthTokens = client.post("/auth/register") {
        header(HttpHeaders.Authorization, "Bearer ${zeroGamesGuest.accessToken}")
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest("zerogames-$roomId@example.com", "correct horse battery staple", "ZeroGames"))
      }.body()

      val zeroWordsGuest: AuthTokens = client.post("/auth/guest") {
        contentType(ContentType.Application.Json)
        setBody(GuestAuthRequest("ZeroWords"))
      }.body()
      val zeroWords: AuthTokens = client.post("/auth/register") {
        header(HttpHeaders.Authorization, "Bearer ${zeroWordsGuest.accessToken}")
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest("zerowords-$roomId@example.com", "correct horse battery staple", "ZeroWords"))
      }.body()

      val playerStatsRepository = PlayerStatsRepository(database)
      // gamesPlayed at the zero boundary, totalWords nonzero: averageScore/averageWords must stay
      // 0.0 (not a division by zero), while averagePointsPerWord, governed by totalWords instead of
      // gamesPlayed, still divides normally.
      playerStatsRepository.replace(
        PlayerStatsRow(
          playerId = zeroGames.playerId,
          totalScore = 50,
          totalWords = 20,
          bestGameScore = 0,
          bestWord = null,
          bestWordScore = 0,
          gamesPlayed = 0,
          gamesCompleted = 0,
          bestRank = null,
          totalXp = 0,
        ),
      )
      // totalWords at the zero boundary, gamesPlayed nonzero: averagePointsPerWord must stay 0.0,
      // while averageScore, governed by gamesPlayed instead, still divides normally.
      playerStatsRepository.replace(
        PlayerStatsRow(
          playerId = zeroWords.playerId,
          totalScore = 99,
          totalWords = 0,
          bestGameScore = 0,
          bestWord = null,
          bestWordScore = 0,
          gamesPlayed = 5,
          gamesCompleted = 0,
          bestRank = null,
          totalXp = 0,
        ),
      )

      val zeroGamesStats: LifetimeStats = client.get(
        "/players/me/stats"
      ) { header(HttpHeaders.Authorization, "Bearer ${zeroGames.accessToken}") }.body()
      zeroGamesStats.averageScore shouldBe 0.0
      zeroGamesStats.averageWords shouldBe 0.0
      zeroGamesStats.averagePointsPerWord shouldBe 2.5

      val zeroWordsStats: LifetimeStats = client.get(
        "/players/me/stats"
      ) { header(HttpHeaders.Authorization, "Bearer ${zeroWords.accessToken}") }.body()
      zeroWordsStats.averageScore shouldBe 19.8
      zeroWordsStats.averageWords shouldBe 0.0
      zeroWordsStats.averagePointsPerWord shouldBe 0.0
    }
  }

  test("players/me/rounds clamps an out-of-range limit instead of applying it as given") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()
      val guest: AuthTokens = client.post("/auth/guest") {
        contentType(ContentType.Application.Json)
        setBody(GuestAuthRequest("LimitPlayer"))
      }.body()

      val roundRepository = RoundRepository(database)
      val roundResultRepository = RoundResultRepository(database)
      val playerRepository = PlayerRepository(database)
      repeat(3) {
        val round = roundRepository.insertFakeFinishedRound(roomId)
        playerRepository.transaction {
          roundResultRepository.insert(
            this,
            RoundResultRow(round, guest.playerId, score = 1, words = 1, rank = 1, xp = 1, enteredAt = Instant.now())
          )
        }
      }

      // limit=0 is below the allowed range: PlayerRoutes.kt coerces it up to 1, not down to 0.
      val zeroLimit = client.get("/players/me/rounds?limit=0") {
        header(HttpHeaders.Authorization, "Bearer ${guest.accessToken}")
      }
      zeroLimit.status shouldBe HttpStatusCode.OK
      val zeroLimitHistory: List<RoundHistoryEntry> = zeroLimit.body()
      zeroLimitHistory shouldHaveSize 1

      // A limit inside the allowed range is passed through untouched, not clamped further.
      val explicitLimit = client.get("/players/me/rounds?limit=2") {
        header(HttpHeaders.Authorization, "Bearer ${guest.accessToken}")
      }
      explicitLimit.status shouldBe HttpStatusCode.OK
      val explicitLimitHistory: List<RoundHistoryEntry> = explicitLimit.body()
      explicitLimitHistory shouldHaveSize 2
    }
  }

  test("players/me/rounds lists newest-first round history with labelled words") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()
      val guest: AuthTokens = client.post("/auth/guest") {
        contentType(ContentType.Application.Json)
        setBody(GuestAuthRequest("HistoryPlayer"))
      }.body()

      val roundRepository = RoundRepository(database)
      val roundResultRepository = RoundResultRepository(database)
      val playerRepository = PlayerRepository(database)
      val round = roundRepository.insertFakeFinishedRound(roomId)
      playerRepository.transaction {
        roundResultRepository.insert(
          this,
          RoundResultRow(round, guest.playerId, score = 12, words = 1, rank = 1, xp = 2, enteredAt = Instant.now())
        )
      }

      val response = client.get(
        "/players/me/rounds"
      ) { header(HttpHeaders.Authorization, "Bearer ${guest.accessToken}") }
      response.status shouldBe HttpStatusCode.OK
      val history: List<RoundHistoryEntry> = response.body()

      history shouldHaveSize 1
      history.first().roundId shouldBe round
      history.first().rank shouldBe 1
      history.first().totalPlayers shouldBe 1
    }
  }

  test("requests without a bearer token are unauthorized") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()

      client.get("/players/me").status shouldBe HttpStatusCode.Unauthorized
    }
  }

  test("DELETE players/me answers 401 without a bearer token") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()

      client.delete("/players/me").status shouldBe HttpStatusCode.Unauthorized
    }
  }

  test("DELETE players/me erases a guest and answers 204, then 404 to any further /players/me call") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()
      val guest: AuthTokens = client.post("/auth/guest") {
        contentType(ContentType.Application.Json)
        setBody(GuestAuthRequest("ToDeleteGuest"))
      }.body()

      val response = client.delete("/players/me") {
        header(HttpHeaders.Authorization, "Bearer ${guest.accessToken}")
      }
      response.status shouldBe HttpStatusCode.NoContent

      PlayerRepository(database).findById(guest.playerId).shouldBeNull()
      val profile = client.get("/players/me") { header(HttpHeaders.Authorization, "Bearer ${guest.accessToken}") }
      profile.status shouldBe HttpStatusCode.NotFound
    }
  }

  test("DELETE players/me erases a registered account, including its email lookup") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()
      val email = "deleteme-$roomId@example.com"
      val registered: AuthTokens = client.post("/auth/register") {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(email, "correct horse battery staple", "Deleteme"))
      }.body()

      val response = client.delete("/players/me") {
        header(HttpHeaders.Authorization, "Bearer ${registered.accessToken}")
      }
      response.status shouldBe HttpStatusCode.NoContent

      PlayerRepository(database).findByEmail(email).shouldBeNull()
    }
  }

  test("DELETE players/me answers 404 the second time, once the account is already gone") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()
      val guest: AuthTokens = client.post("/auth/guest") {
        contentType(ContentType.Application.Json)
        setBody(GuestAuthRequest("TwiceDeleted"))
      }.body()

      client.delete("/players/me") {
        header(HttpHeaders.Authorization, "Bearer ${guest.accessToken}")
      }.status shouldBe HttpStatusCode.NoContent

      val second = client.delete("/players/me") {
        header(HttpHeaders.Authorization, "Bearer ${guest.accessToken}")
      }
      second.status shouldBe HttpStatusCode.NotFound
    }
  }

  test(
    "DELETE players/me clears refresh_tokens, submissions, round_results and player_stats, leaving " +
      "rounds, round_words and another player's own result intact"
  ) {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()

      val deletedGuest: AuthTokens = client.post("/auth/guest") {
        contentType(ContentType.Application.Json)
        setBody(GuestAuthRequest("TableProofDeleted"))
      }.body()
      val deleted: AuthTokens = client.post("/auth/register") {
        header(HttpHeaders.Authorization, "Bearer ${deletedGuest.accessToken}")
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest("tableproof-$roomId@example.com", "correct horse battery staple", "TableProof"))
      }.body()
      val other: AuthTokens = client.post("/auth/guest") {
        contentType(ContentType.Application.Json)
        setBody(GuestAuthRequest("TableProofBystander"))
      }.body()

      val roundRepository = RoundRepository(database)
      val roundResultRepository = RoundResultRepository(database)
      val playerRepository = PlayerRepository(database)
      val submissionRepository = SubmissionRepository(database)
      val playerStatsRepository = PlayerStatsRepository(database)
      val refreshTokenRepository = RefreshTokenRepository(database)

      // A round with a real (non-empty) solution, so round_words has something to prove untouched.
      val roundId = UUID.randomUUID().toString()
      val now = Instant.now()
      roundRepository.insert(
        RoundRecord(
          id = roundId,
          roomId = roomId,
          seed = 0L,
          board = Board(4, List(16) { Tile("A", 1) }),
          mutator = Mutator.NoMutator,
          themeTitle = "Grade padrão",
          themeSubtitle = "0 palavras comuns",
          commonMin = 0,
          maxScore = 10,
          maxWords = 1,
          startsAt = now,
          endsAt = now.plusSeconds(1),
          status = RoundStatus.Finished,
        ),
        listOf(
          SolvedWord(
            normalized = "CASA",
            display = "casa",
            path = listOf(0, 1, 2, 3),
            score = 10,
            tier = WordTier.Common,
          ),
        ),
      )

      submissionRepository.insert(AcceptedSubmission(roundId, deleted.playerId, "CASA", "casa", 10, listOf(0, 1), now))
      submissionRepository.insert(AcceptedSubmission(roundId, other.playerId, "SOL", "sol", 8, listOf(2, 3), now))
      playerRepository.transaction {
        roundResultRepository.insert(
          this,
          RoundResultRow(roundId, deleted.playerId, score = 10, words = 1, rank = 1, xp = 2, enteredAt = now)
        )
        roundResultRepository.insert(
          this,
          RoundResultRow(roundId, other.playerId, score = 8, words = 1, rank = 2, xp = 1, enteredAt = now)
        )
      }
      playerStatsRepository.replace(
        PlayerStatsRow(
          playerId = deleted.playerId,
          totalScore = 10,
          totalWords = 1,
          bestGameScore = 10,
          bestWord = "casa",
          bestWordScore = 10,
          gamesPlayed = 1,
          gamesCompleted = 1,
          bestRank = 1,
          totalXp = 2,
        ),
      )

      val response = client.delete("/players/me") {
        header(HttpHeaders.Authorization, "Bearer ${deleted.accessToken}")
      }
      response.status shouldBe HttpStatusCode.NoContent

      // Every table that points to the deleted player has lost its line.
      playerRepository.findById(deleted.playerId).shouldBeNull()
      refreshTokenRepository.findByHash(TokenHasher.hash(deleted.refreshToken)).shouldBeNull()
      submissionRepository.findByRoundAndPlayer(roundId, deleted.playerId) shouldHaveSize 0
      roundResultRepository.findByRound(roundId).map { it.playerId } shouldNotContain deleted.playerId
      playerStatsRepository.get(deleted.playerId).shouldBeNull()

      // The round, its solution and the other player's own participation all survive.
      roundRepository.findById(roundId).shouldNotBeNull()
      roundRepository.loadSolution(roundId) shouldHaveSize 1
      submissionRepository.findByRoundAndPlayer(roundId, other.playerId) shouldHaveSize 1
      val remainingResults = roundResultRepository.findByRound(roundId)
      remainingResults shouldHaveSize 1
      remainingResults.single().playerId shouldBe other.playerId
    }
  }

  test("DELETE players/me closes an already open socket with the AccountDeleted close code") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()
      val guest: AuthTokens = client.post("/auth/guest") {
        contentType(ContentType.Application.Json)
        setBody(GuestAuthRequest("SocketPlayer"))
      }.body()

      val joined = CompletableDeferred<Unit>()
      var closeCode: Short? = null
      var closeMessage: String? = null

      coroutineScope {
        launch {
          client.webSocket("/ws/multiplayer") {
            sendClientMessage(ClientMessage.JoinRoom(sessionToken = guest.accessToken))
            nextServerMessage().shouldBeInstanceOf<ServerMessage.LobbyState>()
            joined.complete(Unit)
            val reason = closeReason.await()
            closeCode = reason?.code
            closeMessage = reason?.message
          }
        }

        joined.await()
        val response = client.delete("/players/me") {
          header(HttpHeaders.Authorization, "Bearer ${guest.accessToken}")
        }
        response.status shouldBe HttpStatusCode.NoContent
      }

      closeCode shouldBe CloseCodes.AccountDeleted
      closeMessage shouldBe "Account deleted"
    }
  }
})
