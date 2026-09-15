// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.stats

import kotlin.math.pow

/**
 * Level curve from dossier 9: `xpForLevel(n) = 100 * n^1.5` is the total XP a player needs to have
 * reached level `n`. [levelForXp] is the inverse: the highest level a total XP amount qualifies for.
 */
object LevelCurve {
  private const val Coefficient = 100.0
  private const val Exponent = 1.5

  /** Total XP required to be at [level] (level 0 requires 0 XP). */
  fun xpForLevel(level: Int): Double {
    require(level >= 0) { "Level must not be negative, got $level" }
    return Coefficient * level.toDouble().pow(Exponent)
  }

  /** Highest level whose [xpForLevel] does not exceed [totalXp]. */
  fun levelForXp(totalXp: Int): Int {
    require(totalXp >= 0) { "Total XP must not be negative, got $totalXp" }
    if (totalXp == 0) return 0

    // A direct inverse of xpForLevel as a starting guess, corrected below for floating point drift.
    var level = (totalXp / Coefficient).pow(1.0 / Exponent).toInt()
    while (xpForLevel(level + 1) <= totalXp) level++
    while (level > 0 && xpForLevel(level) > totalXp) level--
    return level
  }
}
