// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.network

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

class ReconnectBackoffTest : FunSpec({

  test("The delay doubles with each attempt, starting from the base delay") {
    val backoff = ReconnectBackoff(baseDelayMs = 1_000, maxDelayMs = 1_000_000)
    backoff.delayForAttempt(0) shouldBe 1_000
    backoff.delayForAttempt(1) shouldBe 2_000
    backoff.delayForAttempt(2) shouldBe 4_000
    backoff.delayForAttempt(3) shouldBe 8_000
  }

  test("The delay never exceeds the configured maximum") {
    val backoff = ReconnectBackoff(baseDelayMs = 1_000, maxDelayMs = 5_000)
    checkAll(Arb.int(0..1_000)) { attempt ->
      (backoff.delayForAttempt(attempt) <= 5_000) shouldBe true
    }
  }

  test("The delay is never negative, even for a very large attempt number") {
    val backoff = ReconnectBackoff(baseDelayMs = 1_000, maxDelayMs = 30_000)
    checkAll(Arb.int(0..10_000)) { attempt ->
      (backoff.delayForAttempt(attempt) >= 0) shouldBe true
    }
  }

  test("A negative attempt is rejected") {
    shouldThrow<IllegalArgumentException> { ReconnectBackoff().delayForAttempt(-1) }
  }
})
