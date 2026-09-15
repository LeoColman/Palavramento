// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server

import br.com.colman.palavramento.server.testsupport.TestLexicon
import br.com.colman.palavramento.server.testsupport.testDatabase
import br.com.colman.palavramento.server.testsupport.testRoomId
import br.com.colman.palavramento.server.testsupport.testServerConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication

class ApplicationTest : FunSpec({
  val database = testDatabase()

  test("Health endpoint answers ok") {
    testApplication {
      application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = testRoomId()) }
      client.get("/health").bodyAsText() shouldBe "ok"
    }
  }
})
