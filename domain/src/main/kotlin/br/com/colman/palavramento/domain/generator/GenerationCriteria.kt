// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.generator

/**
 * Acceptance criteria for a generated board (dossier 3, step 4). Every field is a parameter so
 * tests can inject criteria a tiny synthetic lexicon can actually satisfy: the real defaults below
 * are unreachable with a lexicon of only a few thousand words.
 */
data class GenerationCriteria(
  val commonMin: Int = DefaultCommonMin,
  val maxScoreRange: IntRange = DefaultMaxScoreRange,
  val totalWordsMin: Int = DefaultTotalWordsMin,
  val maxAttempts: Int = DefaultMaxAttempts,
  val relaxationFactor: Double = DefaultRelaxationFactor,
) {
  /**
   * The criteria used for the next block of [maxAttempts] attempts after this one fails
   * [maxAttempts] times in a row (dossier 3, step 5: "relax criteria by 10% and repeat").
   *
   * Relaxing by [relaxationFactor] means: [commonMin] and [totalWordsMin] shrink by that fraction
   * (floored, never below zero), and [maxScoreRange] widens by that fraction on each side (its
   * lower bound shrinks, its upper bound grows), making every criterion strictly easier to satisfy.
   * Relaxation is cumulative: applying it again relaxes the already-relaxed criteria further, so
   * repeated failures eventually make acceptance certain regardless of the starting criteria.
   */
  fun relaxed(): GenerationCriteria = copy(
    commonMin = shrink(commonMin),
    totalWordsMin = shrink(totalWordsMin),
    maxScoreRange = shrink(maxScoreRange.first)..grow(maxScoreRange.last),
  )

  private fun shrink(value: Int): Int = (value * (1 - relaxationFactor)).toInt().coerceAtLeast(0)

  private fun grow(value: Int): Int = (value * (1 + relaxationFactor)).toInt()

  companion object {
    const val DefaultCommonMin = 15
    const val DefaultTotalWordsMin = 150
    const val DefaultMaxAttempts = 200
    const val DefaultRelaxationFactor = 0.1
    private const val MinScore = 2500
    private const val MaxScore = 6000
    val DefaultMaxScoreRange = MinScore..MaxScore
  }
}
