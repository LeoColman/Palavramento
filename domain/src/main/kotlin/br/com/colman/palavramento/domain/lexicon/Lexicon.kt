// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.lexicon

/**
 * Read-only word list, walked one normalized letter (A-Z) at a time.
 *
 * Nodes are opaque [Int] handles so implementations can stay array-backed: the solver walks
 * thousands of prefixes per board and must not allocate per step. A handle is only meaningful to
 * the lexicon that produced it, except [Root], which every implementation uses for the empty prefix.
 */
interface Lexicon {
  /** Node reached from [node] through [letter], or [NoNode] when no word continues that way. */
  fun child(node: Int, letter: Char): Int

  /** Entry for the word spelled by the path to [node], or null when that prefix is not a word. */
  fun entry(node: Int): LexiconEntry?

  companion object {
    /** Node for the empty prefix. */
    const val Root = 0
    const val NoNode = -1
  }
}

/**
 * One scoring unit of the lexicon: every canonical form that normalizes to the same letters.
 *
 * `pais` and `país` collapse into one entry (dossier §1.6). [display] is the most frequent of them,
 * and [frequencyRank] its rank in the frequency list, 1 being the most frequent word.
 */
data class LexiconEntry(val display: String, val frequencyRank: Int) {
  companion object {
    /** Rank of a valid word absent from the frequency list: always expert (dossier §2.3). */
    const val Unranked = Int.MAX_VALUE
  }
}

/** Node reached by spelling [letters] from [from], or [Lexicon.NoNode]. */
fun Lexicon.walk(letters: CharSequence, from: Int = Lexicon.Root): Int {
  var node = from
  for (letter in letters) {
    if (node == Lexicon.NoNode) return node
    node = child(node, letter)
  }
  return node
}

/** Entry for a normalized word, or null when it is not in the lexicon. */
fun Lexicon.lookup(normalized: CharSequence): LexiconEntry? {
  val node = walk(normalized)
  return if (node == Lexicon.NoNode) null else entry(node)
}
