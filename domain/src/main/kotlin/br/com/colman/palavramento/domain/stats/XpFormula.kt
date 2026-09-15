// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.stats

import kotlin.math.floor

/**
 * XP awarded for a round (dossier 9): `floor(points / pointsDivisor) + bonusXp` when the player
 * found at least [bonusWordThreshold] words. A parameter object, as the dossier explicitly calls
 * the formula "parametro de configuracao", not a hardcoded constant.
 */
data class XpFormula(
  val pointsDivisor: Int = DefaultPointsDivisor,
  val bonusWordThreshold: Int = DefaultBonusWordThreshold,
  val bonusXp: Int = DefaultBonusXp,
) {
  fun compute(points: Int, words: Int): Int {
    val base = floor(points.toDouble() / pointsDivisor).toInt()
    val bonus = if (words >= bonusWordThreshold) bonusXp else 0
    return base + bonus
  }

  companion object {
    private const val DefaultPointsDivisor = 5
    private const val DefaultBonusWordThreshold = 10
    private const val DefaultBonusXp = 5
    val Default = XpFormula()
  }
}
