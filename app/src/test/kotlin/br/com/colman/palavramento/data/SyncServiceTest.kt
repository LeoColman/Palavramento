// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver.Companion.IN_MEMORY
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.protocol.AuthTokens
import br.com.colman.palavramento.domain.protocol.LifetimeStats
import br.com.colman.palavramento.domain.protocol.PalavramentoJson
import br.com.colman.palavramento.domain.protocol.PlayerProfile
import br.com.colman.palavramento.domain.protocol.RoundHistoryEntry
import br.com.colman.palavramento.domain.stats.RoundStats
import br.com.colman.palavramento.network.KtorRestApi
import br.com.colman.palavramento.network.RestApi
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer

private fun sampleProfile(isGuest: Boolean) =
  PlayerProfile("p1", "Ana", isGuest, level = 2, totalXp = 150, xpForNextLevel = 283)

private fun sampleStats() = LifetimeStats(150, 15, 100, "casa", 12, 2, 2, 75.0, 7.5, 10.0, 1)

private fun sampleRound(roundId: String = "round-1") = RoundHistoryEntry(
  roundId = roundId,
  startsAt = 1_000L,
  board = List(16) { Tile("A", 1) },
  mutator = Mutator.NoMutator,
  themeTitle = "Grade padrao",
  themeSubtitle = "15 palavras comuns",
  maxScore = 4193,
  maxWords = 272,
  stats = RoundStats(73, 6, 12.2, 3.5, 0, 12.2, 14),
  rank = 1,
  totalPlayers = 1,
  words = emptyList(),
)

private fun MockRequestHandleScope.jsonOk(content: String) =
  respond(content, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

private fun restApiOf(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): RestApi {
  val client = HttpClient(MockEngine(handler)) {
    expectSuccess = true
    install(ContentNegotiation) { json(PalavramentoJson) }
  }
  return KtorRestApi(client, "http://test")
}

/** [SyncService] against a Ktor `MockEngine` (task brief: "sync logic against a MockEngine, including offline"). */
class SyncServiceTest : FunSpec({

  fun newCache(): Pair<ProfileRepository, HistoryRepository> {
    val driver = JdbcSqliteDriver(IN_MEMORY)
    Database.Schema.create(driver)
    val database = Database(driver)
    return SqlDelightProfileRepository(database) to SqlDelightHistoryRepository(database)
  }

  test("sync writes profile, stats and rounds to the cache for a registered player") {
    runTest {
      val profile = sampleProfile(isGuest = false)
      val stats = sampleStats()
      val round = sampleRound()
      val restApi = restApiOf { request ->
        when (request.url.encodedPath) {
          "/players/me" -> jsonOk(PalavramentoJson.encodeToString(PlayerProfile.serializer(), profile))
          "/players/me/stats" -> jsonOk(PalavramentoJson.encodeToString(LifetimeStats.serializer(), stats))
          "/players/me/rounds" -> jsonOk(
            PalavramentoJson.encodeToString(ListSerializer(RoundHistoryEntry.serializer()), listOf(round)),
          )

          else -> error("Unexpected request: ${request.url}")
        }
      }
      val (profileRepository, historyRepository) = newCache()
      val tokenRepository = FakeTokenRepository(sampleTokens(isGuest = false))
      val authController = AuthController(restApi, tokenRepository, profileRepository, historyRepository)
      val syncService = SyncService(restApi, authController, profileRepository, historyRepository)

      syncService.sync() shouldBe true

      profileRepository.profile().first() shouldBe profile
      profileRepository.stats().first() shouldBe stats
      historyRepository.rounds().first() shouldBe listOf(round)
    }
  }

  test("sync never calls /players/me/stats for a guest (ADR 0007: the server answers 403 anyway)") {
    runTest {
      val profile = sampleProfile(isGuest = true)
      val restApi = restApiOf { request ->
        when (request.url.encodedPath) {
          "/players/me" -> jsonOk(PalavramentoJson.encodeToString(PlayerProfile.serializer(), profile))
          "/players/me/stats" -> error("A guest must never call /players/me/stats")
          "/players/me/rounds" -> jsonOk(
            PalavramentoJson.encodeToString(ListSerializer(RoundHistoryEntry.serializer()), emptyList())
          )
          else -> error("Unexpected request: ${request.url}")
        }
      }
      val (profileRepository, historyRepository) = newCache()
      val tokenRepository = FakeTokenRepository(sampleTokens(isGuest = true))
      val authController = AuthController(restApi, tokenRepository, profileRepository, historyRepository)
      val syncService = SyncService(restApi, authController, profileRepository, historyRepository)

      syncService.sync() shouldBe true

      profileRepository.profile().first() shouldBe profile
      profileRepository.stats().first() shouldBe null
    }
  }

  test("offline: a network failure during sync leaves the cache exactly as it was, still readable") {
    runTest {
      val (profileRepository, historyRepository) = newCache()
      val cachedProfile = sampleProfile(isGuest = false)
      val cachedStats = sampleStats()
      val cachedRound = sampleRound("cached-round")
      profileRepository.saveProfile(cachedProfile)
      profileRepository.saveStats(cachedStats)
      historyRepository.upsertAll(listOf(cachedRound))

      val restApi = restApiOf { throw java.io.IOException("network down") }
      val tokenRepository = FakeTokenRepository(sampleTokens(isGuest = false))
      val authController = AuthController(restApi, tokenRepository, profileRepository, historyRepository)
      val syncService = SyncService(restApi, authController, profileRepository, historyRepository)

      syncService.sync() shouldBe false

      // The acceptance criterion this test is for: "Historico visivel offline" - the cache from
      // before the failed sync is still there and still readable, not cleared or corrupted.
      profileRepository.profile().first() shouldBe cachedProfile
      profileRepository.stats().first() shouldBe cachedStats
      historyRepository.rounds().first() shouldBe listOf(cachedRound)
    }
  }

  test("offline on the very first sync leaves an empty but readable cache, no crash") {
    runTest {
      val (profileRepository, historyRepository) = newCache()
      val restApi = restApiOf { throw java.io.IOException("network down") }
      val tokenRepository = FakeTokenRepository(sampleTokens(isGuest = false))
      val authController = AuthController(restApi, tokenRepository, profileRepository, historyRepository)
      val syncService = SyncService(restApi, authController, profileRepository, historyRepository)

      syncService.sync() shouldBe false

      profileRepository.profile().first() shouldBe null
      historyRepository.rounds().first().shouldBeEmpty()
    }
  }

  isolationMode = IsolationMode.InstancePerTest
})

private fun sampleTokens(isGuest: Boolean) = AuthTokens(
  playerId = "p1",
  displayName = "Ana",
  isGuest = isGuest,
  accessToken = "access",
  accessTokenExpiresAt = System.currentTimeMillis() + 15 * 60_000L,
  refreshToken = "refresh",
)
