// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.round

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * [GameClock]'s two implementations. [MutableGameClock]'s advance/set arithmetic is already pinned
 * by [RoundTimingTest] against a fixed non-epoch instant; this spec covers what that one does not:
 * the default constructor value and the production clock actually reading the wall clock.
 */
class GameClockTest : FunSpec({
  test("MutableGameClock starts at the epoch when no initial instant is given") {
    val clock = MutableGameClock()

    clock.now() shouldBe Instant.EPOCH
  }

  test("SystemGameClock reads the real wall clock, not a fixed instant") {
    val before = Instant.now()

    val reading = SystemGameClock.now()

    val after = Instant.now()
    (reading.isBefore(before)) shouldBe false
    (reading.isAfter(after)) shouldBe false
  }
})
