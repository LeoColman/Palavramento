// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.ws

import br.com.colman.palavramento.server.round.MutableGameClock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

/** Pure fixed-window rate limiter tests (dossier phase 3 task: "10 submissions/s per connection"). */
class RateLimiterTest : FunSpec({
  test("allows up to the configured maximum within one window") {
    val clock = MutableGameClock(Instant.parse("2026-01-01T00:00:00Z"))
    val limiter = RateLimiter(maxPerSecond = 3, clock = clock)

    limiter.tryAcquire() shouldBe true
    limiter.tryAcquire() shouldBe true
    limiter.tryAcquire() shouldBe true
    limiter.tryAcquire() shouldBe false
  }

  test("resets once a new one-second window begins") {
    val clock = MutableGameClock(Instant.parse("2026-01-01T00:00:00Z"))
    val limiter = RateLimiter(maxPerSecond = 2, clock = clock)

    limiter.tryAcquire() shouldBe true
    limiter.tryAcquire() shouldBe true
    limiter.tryAcquire() shouldBe false

    clock.advance(1000)

    limiter.tryAcquire() shouldBe true
  }

  test("does not reset before a full second has passed") {
    val clock = MutableGameClock(Instant.parse("2026-01-01T00:00:00Z"))
    val limiter = RateLimiter(maxPerSecond = 1, clock = clock)

    limiter.tryAcquire() shouldBe true
    clock.advance(999)
    limiter.tryAcquire() shouldBe false
  }
})
