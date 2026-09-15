// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.stats

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class XpFormulaTest : FunSpec({
  test("XP is floor(points / 5) with fewer than 10 words") {
    XpFormula.Default.compute(points = 73, words = 6) shouldBe 14 // floor(73/5) = 14
  }

  test("Finding 10 or more words adds the bonus") {
    XpFormula.Default.compute(points = 73, words = 10) shouldBe 19 // 14 + 5
    XpFormula.Default.compute(points = 73, words = 9) shouldBe 14
  }

  test("A custom formula honors its own divisor and bonus") {
    val formula = XpFormula(pointsDivisor = 10, bonusWordThreshold = 5, bonusXp = 2)
    formula.pointsDivisor shouldBe 10
    formula.bonusWordThreshold shouldBe 5
    formula.bonusXp shouldBe 2
    formula.compute(points = 55, words = 5) shouldBe 7 // floor(55/10) + 2 = 5 + 2
    formula.compute(points = 55, words = 4) shouldBe 5
  }

  test("Zero points and zero words give zero XP") {
    XpFormula.Default.compute(points = 0, words = 0) shouldBe 0
  }
})
