// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.stats

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RoundStatsTest : FunSpec({
  test("Computes points, words and averages for a handful of accepted words") {
    val words = listOf(
      AcceptedWord(score = 17, length = 4, acceptedAtEpochMs = 1_000),
      AcceptedWord(score = 13, length = 3, acceptedAtEpochMs = 2_000),
      AcceptedWord(score = 9, length = 3, acceptedAtEpochMs = 3_000),
      AcceptedWord(score = 19, length = 4, acceptedAtEpochMs = 4_000),
      AcceptedWord(score = 8, length = 4, acceptedAtEpochMs = 5_000),
      AcceptedWord(score = 7, length = 3, acceptedAtEpochMs = 6_000),
    )
    val stats = RoundStatsCalculator.compute(startedAtEpochMs = 0, acceptedWords = words)

    stats.points shouldBe 73
    stats.words shouldBe 6
    stats.averagePoints shouldBe 12.2 // 73 / 6 = 12.1666... rounded to 1 decimal
    stats.averageLength shouldBe 3.5
    stats.secondsPerWord shouldBe 1.0 // (6000 - 0) / 1000 / 6 = 1.0
    stats.bonusPoints shouldBe 0
  }

  test("No accepted words gives all-zero stats, not a division error") {
    val stats = RoundStatsCalculator.compute(startedAtEpochMs = 1_000, acceptedWords = emptyList())
    stats.points shouldBe 0
    stats.words shouldBe 0
    stats.secondsPerWord shouldBe 0.0
    stats.averageLength shouldBe 0.0
    stats.averagePoints shouldBe 0.0
    stats.xp shouldBe 0
  }

  test("Uses the last accepted word's instant, not the first, for seconds per word") {
    val words = listOf(
      AcceptedWord(score = 1, length = 3, acceptedAtEpochMs = 500),
      AcceptedWord(score = 1, length = 3, acceptedAtEpochMs = 10_500),
    )
    val stats = RoundStatsCalculator.compute(startedAtEpochMs = 500, acceptedWords = words)
    stats.secondsPerWord shouldBe 5.0 // (10500 - 500) / 1000 / 2 = 5.0
  }

  test("bonusPoints is separate from points and defaults to 0") {
    val words = listOf(AcceptedWord(score = 10, length = 3, acceptedAtEpochMs = 1_000))
    val stats = RoundStatsCalculator.compute(startedAtEpochMs = 0, acceptedWords = words, bonusPoints = 30)
    stats.points shouldBe 10
    stats.bonusPoints shouldBe 30
  }

  test("XP uses the configured formula") {
    val words = listOf(AcceptedWord(score = 100, length = 3, acceptedAtEpochMs = 1_000))
    val stats = RoundStatsCalculator.compute(startedAtEpochMs = 0, acceptedWords = words, xpFormula = XpFormula.Default)
    stats.xp shouldBe 20 // floor(100/5), fewer than 10 words
  }
})
