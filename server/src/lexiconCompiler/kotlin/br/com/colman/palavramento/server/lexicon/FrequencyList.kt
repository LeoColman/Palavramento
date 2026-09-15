// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

/**
 * `pt_br_50k.txt` (FrequencyWords/OpenSubtitles 2018, ADR 0002): one `word count` pair per line,
 * most frequent first, already lowercase. Rank is the 1-based line number of a word's own lowercase
 * spelling, diacritics kept, per dossier §2.3: `país` and `pais` are looked up separately, because
 * that difference is exactly what the collapsing tie-break in [CanonicalForms] needs to resolve.
 */
class FrequencyList private constructor(private val rankByLowercaseWord: Map<String, Int>) {
  /** Rank of [lowercaseWord] (1 = most frequent), or null when it is absent from the list. */
  fun rankOf(lowercaseWord: String): Int? = rankByLowercaseWord[lowercaseWord]

  companion object {
    /** Parses `word count` lines. If a word repeats, the first (most frequent) line wins the rank. */
    fun parse(lines: List<String>): FrequencyList {
      val ranks = LinkedHashMap<String, Int>()
      lines.forEachIndexed { index, line ->
        if (line.isBlank()) return@forEachIndexed
        val word = line.substringBefore(' ')
        ranks.putIfAbsent(word, index + 1)
      }
      return FrequencyList(ranks)
    }
  }
}
