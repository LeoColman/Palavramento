// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import br.com.colman.palavramento.domain.lexicon.LexiconEntry

/**
 * Collapses [FormRow]s that share a normalized spelling into one [LexiconEntry] per normalized form
 * (dossier §1.6, §2.3): `pais` and `país` are the same scoring entry, ranked by whichever of the two
 * is more frequent, displayed as whichever spelling that best-ranked form is.
 *
 * Tie-break, when two canonical forms collapse to the same normalized entry with the same rank (most
 * commonly: neither is in the frequency list, so both are [LexiconEntry.Unranked]): the
 * lexicographically smaller canonical form wins. This has no linguistic meaning, it exists only so
 * the artifact is byte-identical for the same inputs regardless of iteration order (ADR 0004).
 */
object CanonicalForms {
  fun collapse(rows: List<FormRow>): Map<String, LexiconEntry> {
    val bestByNormalized = LinkedHashMap<String, LexiconEntry>()
    for (row in rows) {
      val rank = row.rank ?: LexiconEntry.Unranked
      val current = bestByNormalized[row.normalized]
      if (current == null || isBetter(row.canonical, rank, current)) {
        bestByNormalized[row.normalized] = LexiconEntry(row.canonical, rank)
      }
    }
    return bestByNormalized
  }

  private fun isBetter(candidateDisplay: String, candidateRank: Int, current: LexiconEntry): Boolean {
    if (candidateRank != current.frequencyRank) return candidateRank < current.frequencyRank
    return candidateDisplay < current.display
  }
}
