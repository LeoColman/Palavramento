// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.stats

/** One row of a leaderboard before ranking (dossier 6.4). */
data class LeaderboardEntry(val playerId: String, val name: String, val score: Int, val words: Int)

/** [entry] placed at [rank] (1-based, lower is better). */
data class RankedEntry(val rank: Int, val entry: LeaderboardEntry)

/**
 * Turns leaderboard entries into ranks (dossier 6.4), breaking ties deterministically so the same
 * input always produces the same ranking regardless of iteration order:
 * 1. Higher score first.
 * 2. Equal score: more words found first (found more with the same score, so words were shorter
 *    on average or luck was worse, either way a meaningful secondary signal of play).
 * 3. Still equal: player id ascending, the one field guaranteed unique, so the order is total.
 */
object Ranking {
  fun rank(entries: List<LeaderboardEntry>): List<RankedEntry> =
    entries
      .sortedWith(
        compareByDescending<LeaderboardEntry> { it.score }
          .thenByDescending { it.words }
          .thenBy { it.playerId },
      )
      .mapIndexed { index, entry -> RankedEntry(index + 1, entry) }
}
