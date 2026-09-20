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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

private const val ContactEmail = "claude@leonardo.colman.com.br"

/**
 * `GET /privacidade` and `GET /exclusao-de-conta` (ADR 0020): the two static pages the Play Store
 * checks for, without logging in for anything.
 */
class LegalRoutesTest : FunSpec({
  val database = testDatabase()

  test("GET /privacidade answers with the privacy policy as HTML") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }

      val response = testHttpClient().get("/privacidade")

      response.status shouldBe HttpStatusCode.OK
      response.headers[HttpHeaders.ContentType] shouldContain "text/html"
      val body = response.bodyAsText()
      body shouldContain ContactEmail
      body shouldContain "exclusao-de-conta"
    }
  }

  test("GET /exclusao-de-conta answers with the account deletion page as HTML") {
    val roomId = testRoomId()
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }

      val response = testHttpClient().get("/exclusao-de-conta")

      response.status shouldBe HttpStatusCode.OK
      response.headers[HttpHeaders.ContentType] shouldContain "text/html"
      val body = response.bodyAsText()
      body shouldContain ContactEmail
      // The list of what deleting the account wipes out (ADR 0020): tokens, submissions, round
      // results, lifetime stats and the account itself.
      body shouldContain "token"
      body shouldContain "submeteu"
      body shouldContain "rodada que você jogou"
      body shouldContain "estatísticas de vida"
      body shouldContain "Sua conta: e-mail, senha"
    }
  }
})
