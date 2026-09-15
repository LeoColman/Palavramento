// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.stats

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

class LevelCurveTest : FunSpec({
  test("Level 0 requires 0 XP") {
    LevelCurve.xpForLevel(0) shouldBeExactly 0.0
  }

  test("Level 1 requires 100 XP, matching the dossier formula") {
    LevelCurve.xpForLevel(1) shouldBeExactly 100.0
  }

  test("Level 4 requires 800 XP: 100 * 4^1.5") {
    LevelCurve.xpForLevel(4) shouldBeExactly 800.0
  }

  test("0 total XP is level 0") {
    LevelCurve.levelForXp(0) shouldBe 0
  }

  test("levelForXp is the inverse of xpForLevel at exact thresholds") {
    // xpForLevel(n) = 100 * n^1.5 is only ever a whole number when n is a perfect square (n^1.5 =
    // n * sqrt(n)); at any other level the true threshold is fractional, so truncating it to an Int
    // would round the requirement down and legitimately shift the inverse by one level. Perfect
    // squares sidestep that rounding and let this test check an exact round trip.
    for (root in 0..8) {
      val level = root * root
      LevelCurve.levelForXp(LevelCurve.xpForLevel(level).toInt()) shouldBe level
    }
  }

  test("levelForXp never overshoots: xpForLevel(result) <= totalXp < xpForLevel(result + 1)") {
    checkAll(Arb.int(0..1_000_000)) { totalXp ->
      val level = LevelCurve.levelForXp(totalXp)
      (LevelCurve.xpForLevel(level) <= totalXp) shouldBe true
      (LevelCurve.xpForLevel(level + 1) > totalXp) shouldBe true
    }
  }

  test("Rejects a negative level or negative XP") {
    shouldThrow<IllegalArgumentException> { LevelCurve.xpForLevel(-1) }
    shouldThrow<IllegalArgumentException> { LevelCurve.levelForXp(-1) }
  }
})
