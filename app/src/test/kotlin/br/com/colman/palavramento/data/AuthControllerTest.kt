// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.data

import br.com.colman.palavramento.domain.protocol.AuthTokens
import br.com.colman.palavramento.domain.protocol.PalavramentoJson
import br.com.colman.palavramento.domain.protocol.PlayerProfile
import br.com.colman.palavramento.network.KtorRestApi
import br.com.colman.palavramento.network.RestApi
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger

private fun tokens(
  playerId: String = "p1",
  isGuest: Boolean = true,
  accessToken: String = "access-1",
  refreshToken: String = "refresh-1",
  accessTokenExpiresAt: Long = 0L,
) = AuthTokens(playerId, "Convidado", isGuest, accessToken, accessTokenExpiresAt, refreshToken)

private fun MockRequestHandleScope.jsonOk(body: AuthTokens) = respond(
  content = PalavramentoJson.encodeToString(AuthTokens.serializer(), body),
  status = HttpStatusCode.OK,
  headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

private fun MockRequestHandleScope.jsonOk(body: PlayerProfile) = respond(
  content = PalavramentoJson.encodeToString(PlayerProfile.serializer(), body),
  status = HttpStatusCode.OK,
  headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

private fun restApiOf(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): RestApi {
  val client = HttpClient(MockEngine(handler)) {
    expectSuccess = true
    install(ContentNegotiation) { json(PalavramentoJson) }
  }
  return KtorRestApi(client, "http://test")
}

/** [AuthController] against a Ktor `MockEngine` (task brief: "token refresh and retry"). */
class AuthControllerTest : FunSpec({

  test("bootstrap creates and saves a guest when no tokens exist yet") {
    runTest {
      val newGuest = tokens(playerId = "new-guest")
      val restApi = restApiOf { jsonOk(newGuest) }
      val tokenRepository = FakeTokenRepository()
      val controller = AuthController(restApi, tokenRepository, FakeProfileRepository(), FakeHistoryRepository())

      controller.bootstrap() shouldBe newGuest
      tokenRepository.tokens.first() shouldBe newGuest
    }
  }

  test("bootstrap returns the existing tokens without touching the network") {
    runTest {
      val existing = tokens()
      val restApi = restApiOf { error("The network must not be called when tokens already exist") }
      val controller = AuthController(
        restApi,
        FakeTokenRepository(existing),
        FakeProfileRepository(),
        FakeHistoryRepository()
      )

      controller.bootstrap() shouldBe existing
    }
  }

  test("validAccessToken returns the stored token when it is nowhere near expiring") {
    runTest {
      val current = tokens(accessToken = "still-fresh", accessTokenExpiresAt = 10 * MinuteMs)
      val restApi = restApiOf { error("A fresh token must not trigger a refresh call") }
      val controller = AuthController(
        restApi,
        FakeTokenRepository(current),
        FakeProfileRepository(),
        FakeHistoryRepository(),
        nowMs = { 0L },
      )

      controller.validAccessToken() shouldBe "still-fresh"
    }
  }

  test("validAccessToken refreshes and saves the rotated tokens when close to expiring") {
    runTest {
      val current = tokens(accessToken = "old", refreshToken = "refresh-old", accessTokenExpiresAt = 10_000L)
      val rotated = tokens(accessToken = "new", refreshToken = "refresh-new", accessTokenExpiresAt = 20 * MinuteMs)
      val restApi = restApiOf { jsonOk(rotated) }
      val tokenRepository = FakeTokenRepository(current)
      val controller =
        AuthController(restApi, tokenRepository, FakeProfileRepository(), FakeHistoryRepository(), nowMs = {
          0L
        })

      controller.validAccessToken() shouldBe "new"
      tokenRepository.tokens.first() shouldBe rotated
    }
  }

  test("validAccessToken creates a brand new guest when a guest's refresh token is rejected") {
    runTest {
      val expiredGuest = tokens(playerId = "guest-1", isGuest = true, accessTokenExpiresAt = 0L)
      val freshGuest = tokens(playerId = "guest-2", isGuest = true, accessToken = "fresh-guest-token")
      val restApi = restApiOf { request ->
        if (request.url.encodedPath == "/auth/refresh") {
          respondError(HttpStatusCode.Unauthorized)
        } else {
          jsonOk(freshGuest)
        }
      }
      val tokenRepository = FakeTokenRepository(expiredGuest)
      val profileRepository = FakeProfileRepository()
      val historyRepository = FakeHistoryRepository()
      val controller = AuthController(restApi, tokenRepository, profileRepository, historyRepository, nowMs = { 0L })

      controller.validAccessToken() shouldBe "fresh-guest-token"
      tokenRepository.tokens.first() shouldBe freshGuest
      // A guest recovering from a rejected refresh token starts over: no stale cache from "guest-1".
      profileRepository.clearCount shouldBe 1
      historyRepository.clearCount shouldBe 1
      // A guest had no account to go back to, so there is nothing to ask them to log in again for.
      controller.sessionExpired.first() shouldBe false
    }
  }

  test("a registered player whose refresh token is rejected carries on as a new guest, flagged as expired") {
    runTest {
      val expired = tokens(playerId = "player-1", isGuest = false, accessTokenExpiresAt = 0L)
      val freshGuest = tokens(playerId = "guest-2", isGuest = true, accessToken = "fresh-guest-token")
      val restApi = restApiOf { request ->
        if (request.url.encodedPath == "/auth/refresh") {
          respondError(HttpStatusCode.Unauthorized)
        } else {
          jsonOk(freshGuest)
        }
      }
      val profileRepository = FakeProfileRepository()
      val historyRepository = FakeHistoryRepository()
      val controller =
        AuthController(restApi, FakeTokenRepository(expired), profileRepository, historyRepository, nowMs = { 0L })

      // Still a usable token, so Jogar keeps working; the lobby asks them to log in again meanwhile.
      controller.validAccessToken() shouldBe "fresh-guest-token"
      controller.session.first() shouldBe freshGuest
      controller.sessionExpired.first() shouldBe true
      profileRepository.clearCount shouldBe 1
      historyRepository.clearCount shouldBe 1
    }
  }

  test("concurrent callers share one refresh instead of replaying the refresh token the server just rotated") {
    runTest {
      val current = tokens(accessToken = "old", refreshToken = "refresh-old", accessTokenExpiresAt = 10_000L)
      val rotated = tokens(accessToken = "new", refreshToken = "refresh-new", accessTokenExpiresAt = 20 * MinuteMs)
      val refreshCalls = AtomicInteger()
      val restApi = restApiOf {
        refreshCalls.incrementAndGet()
        // Keeps the first refresh in flight long enough for the second caller to arrive meanwhile.
        delay(100)
        jsonOk(rotated)
      }
      val controller = AuthController(
        restApi,
        FakeTokenRepository(current),
        FakeProfileRepository(),
        FakeHistoryRepository(),
        nowMs = { 0L },
      )

      val accessTokens = List(2) { async { controller.validAccessToken() } }.awaitAll()

      accessTokens shouldBe listOf("new", "new")
      refreshCalls.get() shouldBe 1
    }
  }

  test("validAccessToken keeps the last known token on a network hiccup, not a rejected-token recovery") {
    runTest {
      val current = tokens(accessToken = "old", accessTokenExpiresAt = 0L)
      val restApi = restApiOf { throw java.io.IOException("network down") }
      val tokenRepository = FakeTokenRepository(current)
      val controller =
        AuthController(restApi, tokenRepository, FakeProfileRepository(), FakeHistoryRepository(), nowMs = { 0L })

      controller.validAccessToken() shouldBe "old"
    }
  }

  test("callAuthenticated refreshes and retries once when the request itself comes back 401") {
    runTest {
      // Not close to expiring (task brief 4's proactive path), so the 401 below can only come from
      // the request itself - the case this test targets: "refresh through /auth/refresh on 401".
      val current = tokens(accessToken = "stale", refreshToken = "refresh-1", accessTokenExpiresAt = 10 * MinuteMs)
      val rotated = tokens(accessToken = "rotated", refreshToken = "refresh-2", accessTokenExpiresAt = 20 * MinuteMs)
      val profile = PlayerProfile("p1", "Ana", isGuest = false, level = 1, totalXp = 0, xpForNextLevel = 100)
      val restApi = restApiOf { request ->
        when (request.url.encodedPath) {
          "/auth/refresh" -> jsonOk(rotated)
          "/players/me" -> if (request.headers[HttpHeaders.Authorization] == "Bearer stale") {
            respondError(HttpStatusCode.Unauthorized)
          } else {
            respond(
              content = PalavramentoJson.encodeToString(PlayerProfile.serializer(), profile),
              status = HttpStatusCode.OK,
              headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
          }

          else -> error("Unexpected request: ${request.url}")
        }
      }
      val tokenRepository = FakeTokenRepository(current)
      val controller =
        AuthController(restApi, tokenRepository, FakeProfileRepository(), FakeHistoryRepository(), nowMs = {
          0L
        })

      val result = controller.callAuthenticated { token -> restApi.playerProfile(token) }

      result shouldBe profile
      tokenRepository.tokens.first() shouldBe rotated
    }
  }

  test("callAuthenticated retries with a token another caller rotated meanwhile, without refreshing again") {
    runTest {
      val current = tokens(accessToken = "stale", refreshToken = "refresh-1", accessTokenExpiresAt = 10 * MinuteMs)
      val rotated = tokens(accessToken = "rotated", refreshToken = "refresh-2", accessTokenExpiresAt = 20 * MinuteMs)
      val profile = PlayerProfile("p1", "Ana", isGuest = false, level = 1, totalXp = 0, xpForNextLevel = 100)
      val tokenRepository = FakeTokenRepository(current)
      val refreshCalls = AtomicInteger()
      val restApi = restApiOf { request ->
        when (request.url.encodedPath) {
          "/auth/refresh" -> {
            refreshCalls.incrementAndGet()
            respondError(HttpStatusCode.Unauthorized)
          }

          "/players/me" -> if (request.headers[HttpHeaders.Authorization] == "Bearer stale") {
            // Another caller finished its own refresh while this request was in flight.
            tokenRepository.save(rotated)
            respondError(HttpStatusCode.Unauthorized)
          } else {
            jsonOk(profile)
          }

          else -> error("Unexpected request: ${request.url}")
        }
      }
      val controller =
        AuthController(restApi, tokenRepository, FakeProfileRepository(), FakeHistoryRepository(), nowMs = { 0L })

      controller.callAuthenticated { token -> restApi.playerProfile(token) } shouldBe profile
      refreshCalls.get() shouldBe 0
    }
  }

  test("callAuthenticated gives up when the session is gone by the time it would retry") {
    runTest {
      val current = tokens(accessToken = "stale", accessTokenExpiresAt = 10 * MinuteMs)
      val tokenRepository = FakeTokenRepository(current)
      val restApi = restApiOf {
        tokenRepository.clear()
        respondError(HttpStatusCode.Unauthorized)
      }
      val controller =
        AuthController(restApi, tokenRepository, FakeProfileRepository(), FakeHistoryRepository(), nowMs = { 0L })

      controller.callAuthenticated { token -> restApi.playerProfile(token) } shouldBe null
    }
  }

  test("callAuthenticated returns null when it cannot get a token at all") {
    runTest {
      val restApi = restApiOf { respondError(HttpStatusCode.Unauthorized) }
      val expired = tokens(isGuest = false, accessTokenExpiresAt = 0L)
      val tokenRepository = FakeTokenRepository(expired)
      val controller =
        AuthController(restApi, tokenRepository, FakeProfileRepository(), FakeHistoryRepository(), nowMs = { 0L })

      controller.callAuthenticated { "unreachable" } shouldBe null
    }
  }

  test("register success saves the promoted tokens and clears the local cache") {
    runTest {
      val guest =
        tokens(playerId = "guest-1", isGuest = true, accessToken = "guest-token", accessTokenExpiresAt = 10 * MinuteMs)
      val promoted = tokens(playerId = "guest-1", isGuest = false, accessToken = "promoted-token")
      var sentBearer: String? = null
      val restApi = restApiOf { request ->
        sentBearer = request.headers[HttpHeaders.Authorization]
        jsonOk(promoted)
      }
      val tokenRepository = FakeTokenRepository(guest)
      val profileRepository = FakeProfileRepository()
      val controller =
        AuthController(restApi, tokenRepository, profileRepository, FakeHistoryRepository(), nowMs = { 0L })

      val result = controller.register("ana@example.com", "correct horse battery staple", "Ana")

      result shouldBe AuthCallResult.Success
      tokenRepository.tokens.first() shouldBe promoted
      sentBearer shouldBe "Bearer guest-token"
      profileRepository.clearCount shouldBe 1
    }
  }

  test("register maps a 409 Conflict to EmailTaken") {
    runTest {
      val restApi = restApiOf { respondError(HttpStatusCode.Conflict) }
      val controller = AuthController(restApi, FakeTokenRepository(), FakeProfileRepository(), FakeHistoryRepository())

      controller.register("taken@example.com", "correct horse battery staple", "Ana") shouldBe AuthCallResult.EmailTaken
    }
  }

  test("register maps a 401 (no guest session) to GuestSessionNotFound") {
    runTest {
      val restApi = restApiOf { respondError(HttpStatusCode.Unauthorized) }
      val controller = AuthController(restApi, FakeTokenRepository(), FakeProfileRepository(), FakeHistoryRepository())

      val result = controller.register("ana@example.com", "correct horse battery staple", "Ana")
      result shouldBe AuthCallResult.GuestSessionNotFound
    }
  }

  test("register maps a network failure to NetworkError") {
    runTest {
      val restApi = restApiOf { throw java.io.IOException("offline") }
      val controller = AuthController(restApi, FakeTokenRepository(), FakeProfileRepository(), FakeHistoryRepository())

      controller.register("ana@example.com", "correct horse battery staple", "Ana") shouldBe AuthCallResult.NetworkError
    }
  }

  test("login success saves the migrated tokens, clears the local cache and the expired-session notice") {
    runTest {
      val migrated = tokens(playerId = "target-1", isGuest = false)
      val restApi = restApiOf { jsonOk(migrated) }
      val tokenRepository = FakeTokenRepository(sessionExpired = true)
      val historyRepository = FakeHistoryRepository()
      val controller = AuthController(restApi, tokenRepository, FakeProfileRepository(), historyRepository)

      controller.login("ana@example.com", "correct horse battery staple") shouldBe AuthCallResult.Success
      tokenRepository.tokens.first() shouldBe migrated
      historyRepository.clearCount shouldBe 1
      tokenRepository.sessionExpired.first() shouldBe false
    }
  }

  test("login refreshes an expiring guest token first, so the server can still migrate that guest") {
    runTest {
      val guest = tokens(playerId = "guest-1", accessToken = "guest-old", refreshToken = "refresh-old")
      val rotatedGuest = tokens(
        playerId = "guest-1",
        accessToken = "guest-new",
        refreshToken = "refresh-new",
        accessTokenExpiresAt = 20 * MinuteMs,
      )
      val account = tokens(playerId = "target-1", isGuest = false, accessToken = "account-token")
      var loginBearer: String? = null
      val restApi = restApiOf { request ->
        when (request.url.encodedPath) {
          "/auth/refresh" -> jsonOk(rotatedGuest)
          "/auth/login" -> {
            loginBearer = request.headers[HttpHeaders.Authorization]
            jsonOk(account)
          }

          else -> error("Unexpected request: ${request.url}")
        }
      }
      val tokenRepository = FakeTokenRepository(guest)
      val controller =
        AuthController(restApi, tokenRepository, FakeProfileRepository(), FakeHistoryRepository(), nowMs = { 0L })

      controller.login("ana@example.com", "correct horse battery staple") shouldBe AuthCallResult.Success
      // An expired bearer is a 401 even on /auth/login, which would read as a wrong password.
      loginBearer shouldBe "Bearer guest-new"
      tokenRepository.tokens.first() shouldBe account
    }
  }

  test("login from a registered session sends no guest token") {
    runTest {
      val registered = tokens(playerId = "player-1", isGuest = false, accessToken = "player-token")
      var loginBearer: String? = "not sent yet"
      val restApi = restApiOf { request ->
        loginBearer = request.headers[HttpHeaders.Authorization]
        jsonOk(tokens(playerId = "player-2", isGuest = false))
      }
      val controller =
        AuthController(restApi, FakeTokenRepository(registered), FakeProfileRepository(), FakeHistoryRepository())

      controller.login("bia@example.com", "correct horse battery staple") shouldBe AuthCallResult.Success
      loginBearer shouldBe null
    }
  }

  test("dismissSessionExpired hides the notice without touching the session") {
    runTest {
      val guest = tokens(playerId = "guest-1")
      val restApi = restApiOf { error("Dismissing the notice must not call the network") }
      val tokenRepository = FakeTokenRepository(guest, sessionExpired = true)
      val controller = AuthController(restApi, tokenRepository, FakeProfileRepository(), FakeHistoryRepository())

      controller.dismissSessionExpired()

      controller.sessionExpired.first() shouldBe false
      tokenRepository.tokens.first() shouldBe guest
    }
  }

  test("login maps a 401 to InvalidCredentials") {
    runTest {
      val restApi = restApiOf { respondError(HttpStatusCode.Unauthorized) }
      val controller = AuthController(restApi, FakeTokenRepository(), FakeProfileRepository(), FakeHistoryRepository())

      controller.login("ana@example.com", "wrong-password") shouldBe AuthCallResult.InvalidCredentials
    }
  }

  test("logout clears the session and the cache, then bootstraps a fresh guest") {
    runTest {
      val freshGuest = tokens(playerId = "fresh-guest")
      val restApi = restApiOf { jsonOk(freshGuest) }
      val tokenRepository = FakeTokenRepository(tokens(playerId = "registered-1", isGuest = false))
      val profileRepository = FakeProfileRepository()
      val historyRepository = FakeHistoryRepository()
      val controller = AuthController(restApi, tokenRepository, profileRepository, historyRepository)

      controller.logout()

      tokenRepository.tokens.first() shouldBe freshGuest
      profileRepository.clearCount shouldBe 1
      historyRepository.clearCount shouldBe 1
    }
  }
})

private const val MinuteMs = 60_000L
