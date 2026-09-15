// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.network

import br.com.colman.palavramento.domain.protocol.AuthTokens
import br.com.colman.palavramento.domain.protocol.GuestAuthRequest
import br.com.colman.palavramento.domain.protocol.PalavramentoJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest

private val sampleTokens = AuthTokens(
  playerId = "p1",
  displayName = "Convidado",
  isGuest = true,
  accessToken = "access",
  accessTokenExpiresAt = 1_000,
  refreshToken = "refresh",
)

private fun clientReturning(body: String, path: String): HttpClient {
  val engine = MockEngine { request ->
    request.url.encodedPath shouldBe path
    respond(
      content = body,
      status = HttpStatusCode.OK,
      headers = headersOf(HttpHeaders.ContentType, "application/json")
    )
  }
  return HttpClient(engine) {
    install(ContentNegotiation) { json(PalavramentoJson) }
  }
}

class KtorRestApiTest : FunSpec({

  test("guestAuth posts to /auth/guest and parses the AuthTokens response") {
    runTest {
      val client =
        clientReturning(PalavramentoJson.encodeToString(AuthTokens.serializer(), sampleTokens), "/auth/guest")
      val api = KtorRestApi(client, "http://test")

      api.guestAuth(GuestAuthRequest()) shouldBe sampleTokens
    }
  }

  test("playerProfile reads /players/me") {
    runTest {
      val profileJson = """
        {"playerId":"p1","displayName":"Convidado","isGuest":true,"level":2,"totalXp":150,"xpForNextLevel":283}
      """.trimIndent()
      val client = clientReturning(profileJson, "/players/me")
      val api = KtorRestApi(client, "http://test")

      val profile = api.playerProfile("access")

      profile.playerId shouldBe "p1"
      profile.level shouldBe 2
    }
  }
})
