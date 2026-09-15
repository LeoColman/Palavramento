// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.rest

import br.com.colman.palavramento.domain.protocol.AuthTokens
import br.com.colman.palavramento.domain.protocol.GuestAuthRequest
import br.com.colman.palavramento.domain.protocol.LifetimeStats
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
