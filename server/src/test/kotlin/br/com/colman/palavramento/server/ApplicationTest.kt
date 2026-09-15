// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication

class ApplicationTest : FunSpec({
  test("Health endpoint answers ok") {
    testApplication {
      application { module() }
      client.get("/health").bodyAsText() shouldBe "ok"
    }
  }
})
