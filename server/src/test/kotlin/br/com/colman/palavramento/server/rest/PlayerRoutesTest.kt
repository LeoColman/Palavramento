// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.rest

import br.com.colman.palavramento.domain.protocol.AuthTokens
import br.com.colman.palavramento.domain.protocol.GuestAuthRequest
import br.com.colman.palavramento.domain.protocol.LifetimeStats
import br.com.colman.palavramento.domain.protocol.LoginRequest
import br.com.colman.palavramento.domain.protocol.PlayerProfile
import br.com.colman.palavramento.domain.protocol.RegisterRequest
import br.com.colman.palavramento.domain.protocol.RoundHistoryEntry
import br.com.colman.palavramento.server.module
import br.com.colman.palavramento.server.repository.PlayerRepository
import br.com.colman.palavramento.server.repository.PlayerStatsRepository
import br.com.colman.palavramento.server.repository.PlayerStatsRow
import br.com.colman.palavramento.server.repository.RoundRepository
import br.com.colman.palavramento.server.repository.RoundResultRepository
import br.com.colman.palavramento.server.repository.RoundResultRow
import br.com.colman.palavramento.server.testsupport.TestLexicon
import br.com.colman.palavramento.server.testsupport.insertFakeFinishedRound
import br.com.colman.palavramento.server.testsupport.testDatabase
import br.com.colman.palavramento.server.testsupport.testHttpClient
import br.com.colman.palavramento.server.testsupport.testRoomId
import br.com.colman.palavramento.server.testsupport.testServerConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.time.Instant

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
})
