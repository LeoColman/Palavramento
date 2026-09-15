// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

private const val MinLetters = 3
private const val MaxLetters = 16

/** Straight and curly apostrophes: VERO uses the straight one, the curly one is filtered defensively. */
private val Apostrophes = setOf('\'', '’')
private val PunctuationToDrop = Apostrophes + setOf('-', '.')

/**
 * Filters for a canonical form fresh out of [HunspellExpander] (dossier §2.2): hyphens, apostrophes
 * and dots rule out compounds and abbreviations, digits and uppercase letters rule out numbers,
 * units and proper nouns/acronyms, and the length bounds keep tile paths sane (board words are at
 * least 3 letters, dossier §1.1; 16 is every tile of a 4x4 board).
 */
object LexiconFilters {
  fun isAcceptableCanonicalForm(canonical: String): Boolean = canonical.none {
    it in PunctuationToDrop || it in '0'..'9' || it.isUpperCase()
  }

  /**
   * Safety net over [WordNormalizer]'s output: length is measured here (post-normalization, as the
   * dossier specifies) and any character normalization could not reduce to a plain A-Z letter, for
   * example a superscript digit or a leftover space from a multi-word entry, disqualifies the form.
   */
  fun isAcceptableNormalizedForm(normalized: String): Boolean =
    normalized.length in MinLetters..MaxLetters && normalized.all { it in 'A'..'Z' }
}
