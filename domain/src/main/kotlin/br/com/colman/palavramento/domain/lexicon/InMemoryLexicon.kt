// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.lexicon

import br.com.colman.palavramento.domain.WordNormalizer

/**
 * Map-backed [Lexicon] for small word lists: tests, fixtures and reference implementations.
 *
 * Keys are normalized forms (A-Z only). Fine for a few thousand words; the production lexicon of a
 * million forms uses the compact array trie instead.
 */
class InMemoryLexicon(entries: Map<String, LexiconEntry>) : Lexicon {
  private val children = mutableListOf(HashMap<Char, Int>())
  private val entries = mutableListOf<LexiconEntry?>(null)

  init {
    entries.forEach { (normalized, entry) -> insert(normalized, entry) }
  }

  override fun child(node: Int, letter: Char): Int = children[node][letter] ?: Lexicon.NoNode

  override fun entry(node: Int): LexiconEntry? = entries[node]

  private fun insert(normalized: String, entry: LexiconEntry) {
    require(normalized.isNotEmpty() && normalized.all { it in 'A'..'Z' }) {
      "Lexicon keys must be normalized A-Z words, got '$normalized'"
    }
    var node = Lexicon.Root
    for (letter in normalized) {
      node = children[node].getOrPut(letter) {
        children += HashMap()
        entries += null
        children.lastIndex
      }
    }
    entries[node] = entry
  }

  companion object {
    /** Lexicon where every word is its own display form, ranked by list position. */
    fun of(vararg displayForms: String): InMemoryLexicon = InMemoryLexicon(
      displayForms.withIndex().associate { (index, form) ->
        WordNormalizer.normalize(form) to LexiconEntry(form, index + 1)
      },
    )
  }
}
