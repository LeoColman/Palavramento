// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.generator

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GenerationCriteriaTest : FunSpec({
  test("Relaxing shrinks the minimums by the relaxation factor, floored") {
    val criteria = GenerationCriteria(commonMin = 15, totalWordsMin = 150, relaxationFactor = 0.1)
    val relaxed = criteria.relaxed()
    relaxed.commonMin shouldBe 13 // floor(15 * 0.9) = 13.5 -> 13
    relaxed.totalWordsMin shouldBe 135 // 150 * 0.9 = 135
  }

  test("Relaxing widens the max score range on both ends") {
    val criteria = GenerationCriteria(maxScoreRange = 2500..6000, relaxationFactor = 0.1)
    val relaxed = criteria.relaxed()
    relaxed.maxScoreRange.first shouldBe 2250 // 2500 * 0.9
    relaxed.maxScoreRange.last shouldBe 6600 // 6000 * 1.1
  }

  test("Relaxing never takes a minimum below zero") {
    val criteria = GenerationCriteria(commonMin = 1, totalWordsMin = 0)
    val relaxed = criteria.relaxed().relaxed().relaxed()
    (relaxed.commonMin >= 0) shouldBe true
    (relaxed.totalWordsMin >= 0) shouldBe true
  }

  test("Relaxation is cumulative across repeated calls") {
    val criteria = GenerationCriteria(commonMin = 100, relaxationFactor = 0.1)
    val onceMore = criteria.relaxed().relaxed()
    onceMore.commonMin shouldBe 81 // floor(floor(100*0.9)*0.9) = floor(90*0.9) = 81
  }

  test("maxAttempts and relaxationFactor themselves are not relaxed") {
    val criteria = GenerationCriteria(maxAttempts = 200, relaxationFactor = 0.1)
    val relaxed = criteria.relaxed()
    relaxed.maxAttempts shouldBe 200
    relaxed.relaxationFactor shouldBe 0.1
  }
})
