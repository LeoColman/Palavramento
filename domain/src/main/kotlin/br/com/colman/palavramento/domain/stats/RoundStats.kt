// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.stats

import kotlinx.serialization.Serializable
import kotlin.math.round

/**
 * A player's stats for one finished round (dossier 6.3). Every rate is rounded to one decimal for
 * display, matching the dossier's explicit rule for "Segundos por palavra".
 */
@Serializable
data class RoundStats(
  val points: Int,
  val words: Int,
  val secondsPerWord: Double,
  val averageLength: Double,
  val bonusPoints: Int,
  val averagePoints: Double,
  val xp: Int,
)

/**
 * Computes [RoundStats] from the words a player scored during a round.
 *
 * [bonusPoints] is always 0 for every v1 mutator (dossier 9.3: the source of the 30-point bonus
 * seen in the reference screenshots is unknown, and the dossier explicitly says not to invent
 * behavior beyond "a field only mutators can feed"), kept separate from [RoundStats.points].
 */
object RoundStatsCalculator {
  private const val DecimalScale = 10.0

  fun compute(
    startedAtEpochMs: Long,
    acceptedWords: List<AcceptedWord>,
    bonusPoints: Int = 0,
    xpFormula: XpFormula = XpFormula.Default,
  ): RoundStats {
    val points = acceptedWords.sumOf { it.score }
    val words = acceptedWords.size

    val secondsPerWord = if (words == 0) {
      0.0
    } else {
      val lastAcceptedAt = acceptedWords.maxOf { it.acceptedAtEpochMs }
      roundTo1Decimal((lastAcceptedAt - startedAtEpochMs) / MillisPerSecond / words)
    }
    val averageLength = if (words == 0) 0.0 else roundTo1Decimal(acceptedWords.map { it.length }.average())
    val averagePoints = if (words == 0) 0.0 else roundTo1Decimal(points.toDouble() / words)

    return RoundStats(
      points = points,
      words = words,
      secondsPerWord = secondsPerWord,
      averageLength = averageLength,
      bonusPoints = bonusPoints,
      averagePoints = averagePoints,
      xp = xpFormula.compute(points, words),
    )
  }

  private fun roundTo1Decimal(value: Double): Double = round(value * DecimalScale) / DecimalScale

  private const val MillisPerSecond = 1000.0
}
