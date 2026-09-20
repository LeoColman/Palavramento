// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.rest

import br.com.colman.palavramento.server.module
import br.com.colman.palavramento.server.testsupport.TestLexicon
import br.com.colman.palavramento.server.testsupport.testDatabase
import br.com.colman.palavramento.server.testsupport.testHttpClient
import br.com.colman.palavramento.server.testsupport.testRoomId
import br.com.colman.palavramento.server.testsupport.testServerConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

private const val Token = "metrics-token-for-tests"

/** `GET /metrics` (ADR 0019): who may read it, and what it says. */
class MetricsRoutesTest : FunSpec({
  val database = testDatabase()

  test("the right token gets the gauges, in Prometheus text format") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(metricsToken = Token), database, TestLexicon.lexicon, roomId = roomId) }

      val response = testHttpClient().get("/metrics") {
        header(HttpHeaders.Authorization, "Bearer $Token")
      }

      response.status shouldBe HttpStatusCode.OK
      val body = response.bodyAsText()
      body shouldContain "palavramento_players_connected"
      body shouldContain """palavramento_players_active{window="24h"}"""
      body shouldContain """palavramento_players_accounts{kind="registered"}"""
      // Ktor's own HTTP metrics ride along on the same scrape.
      body shouldContain "ktor_http_server_requests"
    }
  }

  test("no token, wrong token and a token without the Bearer prefix are all rejected") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(metricsToken = Token), database, TestLexicon.lexicon, roomId = roomId) }
      val client = testHttpClient()

      client.get("/metrics").status shouldBe HttpStatusCode.Unauthorized
      client.get("/metrics") { header(HttpHeaders.Authorization, "Bearer wrong") }
        .status shouldBe HttpStatusCode.Unauthorized
      client.get("/metrics") { header(HttpHeaders.Authorization, Token) }
        .status shouldBe HttpStatusCode.Unauthorized
    }
  }

  test("a server with no metrics token does not admit the endpoint exists") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }

      val response = testHttpClient().get("/metrics") {
        header(HttpHeaders.Authorization, "Bearer $Token")
      }

      response.status shouldBe HttpStatusCode.NotFound
    }
  }
})
