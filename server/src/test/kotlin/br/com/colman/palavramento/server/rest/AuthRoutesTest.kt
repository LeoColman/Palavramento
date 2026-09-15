// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.rest

import br.com.colman.palavramento.domain.protocol.AuthTokens
import br.com.colman.palavramento.domain.protocol.GuestAuthRequest
import br.com.colman.palavramento.domain.protocol.LoginRequest
import br.com.colman.palavramento.domain.protocol.RefreshRequest
import br.com.colman.palavramento.domain.protocol.RegisterRequest
import br.com.colman.palavramento.server.module
import br.com.colman.palavramento.server.repository.PlayerRepository
import br.com.colman.palavramento.server.repository.PlayerStatsRepository
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
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.time.Instant

private const val Password = "correct horse battery staple"

/** `/auth/...` (dossier §8, ADR 0007): guest creation, refresh rotation, promotion and login migration. */
class AuthRoutesTest : FunSpec({
  val database = testDatabase()

  suspend fun HttpClient.guest(displayName: String): AuthTokens =
    post("/auth/guest") {
      contentType(ContentType.Application.Json)
      setBody(GuestAuthRequest(displayName))
    }.body()

  suspend fun HttpClient.register(bearer: String?, email: String, displayName: String): HttpResponse =
    post("/auth/register") {
      bearer?.let { header(HttpHeaders.Authorization, "Bearer $it") }
      contentType(ContentType.Application.Json)
      setBody(RegisterRequest(email, Password, displayName))
    }

  suspend fun HttpClient.login(bearer: String?, email: String): HttpResponse =
    post("/auth/login") {
      bearer?.let { header(HttpHeaders.Authorization, "Bearer $it") }
      contentType(ContentType.Application.Json)
      setBody(LoginRequest(email, Password))
    }

  test("guest creates an anonymous player with a default display name") {
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = testRoomId()) }
      val tokens = testHttpClient().guest("")

      tokens.isGuest shouldBe true
      tokens.displayName.startsWith("Convidado") shouldBe true
    }
  }

  test("refresh rotates the token and rejects reuse of an already-rotated one") {
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = testRoomId()) }
      val client = testHttpClient()
      val first = client.guest("Rotator")

      val refreshed = client.post("/auth/refresh") {
        contentType(ContentType.Application.Json)
        setBody(RefreshRequest(first.refreshToken))
      }
      refreshed.status shouldBe HttpStatusCode.OK
      val second: AuthTokens = refreshed.body()
      second.playerId shouldBe first.playerId
      second.refreshToken shouldNotBe first.refreshToken

      val reuse = client.post("/auth/refresh") {
        contentType(ContentType.Application.Json)
        setBody(RefreshRequest(first.refreshToken))
      }
      reuse.status shouldBe HttpStatusCode.Unauthorized

      // Reuse revokes the whole chain: even the latest, legitimately-issued token stops working.
      val afterReuse = client.post("/auth/refresh") {
        contentType(ContentType.Application.Json)
        setBody(RefreshRequest(second.refreshToken))
      }
      afterReuse.status shouldBe HttpStatusCode.Unauthorized
    }
  }

  test("register with a guest's access token promotes that same player, keeping its id") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()
      val guest = client.guest("Promotable")

      // The guest already has a round result before promotion.
      val roundRepository = RoundRepository(database)
      val roundResultRepository = RoundResultRepository(database)
      val playerRepository = PlayerRepository(database)
      val round = roundRepository.insertFakeFinishedRound(roomId)
      playerRepository.transaction {
        roundResultRepository.insert(
          this,
          RoundResultRow(round, guest.playerId, score = 42, words = 3, rank = 1, xp = 8, enteredAt = Instant.now())
        )
      }

      val response = client.register(guest.accessToken, "promoted-$roomId@example.com", "Promoted")
      response.status shouldBe HttpStatusCode.OK
      val promoted: AuthTokens = response.body()

      promoted.playerId shouldBe guest.playerId
      promoted.isGuest shouldBe false

      val player = playerRepository.findById(guest.playerId)
      player?.isGuest shouldBe false
      player?.email shouldBe "promoted-$roomId@example.com"

      // History (and now lifetime stats, since the player is registered) survived the promotion.
      roundResultRepository.findByRound(round) shouldHaveSize 1
      val stats = PlayerStatsRepository(database).get(guest.playerId)
      stats?.totalScore shouldBe 42L
      stats?.gamesPlayed shouldBe 1
    }
  }

  test("registering with an already-used email is rejected") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()
      client.register(null, "taken-$roomId@example.com", "First").status shouldBe HttpStatusCode.OK

      val second = client.register(null, "taken-$roomId@example.com", "Second")
      second.status shouldBe HttpStatusCode.Conflict
    }
  }

  test("login with the wrong password is rejected") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()
      client.register(null, "wrongpass-$roomId@example.com", "Someone").status shouldBe HttpStatusCode.OK

      val response = client.post("/auth/login") {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest("wrongpass-$roomId@example.com", "not the right password"))
      }
      response.status shouldBe HttpStatusCode.Unauthorized
    }
  }

  test("login as a guest into an existing account migrates history, keeping the target's own result on conflict") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()

      val targetGuest = client.guest("TargetOwner")
      client.register(targetGuest.accessToken, "target-$roomId@example.com", "Target").status shouldBe HttpStatusCode.OK
      val targetId = targetGuest.playerId

      val roundRepository = RoundRepository(database)
      val roundResultRepository = RoundResultRepository(database)
      val playerRepository = PlayerRepository(database)
      val sharedRound = roundRepository.insertFakeFinishedRound(roomId)
      val guestOnlyRound = roundRepository.insertFakeFinishedRound(roomId)

      playerRepository.transaction {
        roundResultRepository.insert(
          this,
          RoundResultRow(sharedRound, targetId, score = 100, words = 5, rank = 1, xp = 20, enteredAt = Instant.now())
        )
      }

      val guest = client.guest("MigratingGuest")
      val enteredAt = Instant.now()
      playerRepository.transaction {
        roundResultRepository.insert(
          this,
          RoundResultRow(sharedRound, guest.playerId, score = 50, words = 2, rank = 2, xp = 10, enteredAt = enteredAt)
        )
        roundResultRepository.insert(
          this,
          RoundResultRow(
            guestOnlyRound,
            guest.playerId,
            score = 70,
            words = 3,
            rank = 1,
            xp = 14,
            enteredAt = enteredAt,
          )
        )
      }

      val loginResponse = client.login(guest.accessToken, "target-$roomId@example.com")
      loginResponse.status shouldBe HttpStatusCode.OK
      val merged: AuthTokens = loginResponse.body()
      merged.playerId shouldBe targetId

      // Conflict round: the target's own result wins, the guest's duplicate is dropped.
      val sharedResults = roundResultRepository.findByRound(sharedRound)
      sharedResults shouldHaveSize 1
      sharedResults.first().playerId shouldBe targetId
      sharedResults.first().score shouldBe 100

      // Non-conflicting round: the guest's result moves over intact.
      val guestOnlyResults = roundResultRepository.findByRound(guestOnlyRound)
      guestOnlyResults shouldHaveSize 1
      guestOnlyResults.first().playerId shouldBe targetId
      guestOnlyResults.first().score shouldBe 70

      // The guest player row is gone once nothing references it anymore.
      playerRepository.findById(guest.playerId).shouldBeNull()

      // player_stats rebuilt from the merged round_results: 100 (own) + 70 (migrated) = 170.
      val stats = PlayerStatsRepository(database).get(targetId)
      stats?.totalScore shouldBe 170L
      stats?.gamesPlayed shouldBe 2
    }
  }
})
