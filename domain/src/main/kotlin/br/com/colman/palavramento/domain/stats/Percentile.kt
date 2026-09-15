// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.stats

import kotlin.math.floor

/** Leaderboard percentile (dossier 6.4): `floor((N - rank) / (N - 1) * 100)`, 0 when N = 1. */
object Percentile {
  private const val Scale = 100

  fun of(rank: Int, totalPlayers: Int): Int {
    require(totalPlayers >= 1) { "Total players must be at least 1, got $totalPlayers" }
    require(rank in 1..totalPlayers) { "Rank must be in 1..$totalPlayers, got $rank" }
    if (totalPlayers == 1) return 0
    return floor((totalPlayers - rank).toDouble() / (totalPlayers - 1) * Scale).toInt()
  }
}
