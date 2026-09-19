// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.board

import kotlinx.serialization.Serializable

/**
 * One cell of a [Board]: one or more normalized letters (a digraph like `QU`, `AO`, `NH` or `LH`
 * consumes all of them at once, dossier 1.6) and the point value shown on the tile.
 *
 * [letters] is always uppercase A-Z, with diacritics already stripped by `WordNormalizer`: tiles
 * never carry accents, only the lexicon's display form does. Since ADR 0015 ("Uma ou outra"),
 * [letters] may instead hold two or more alternative spellings separated by `/`, e.g. `A/F`: see
 * [options]. The wire shape and `board_json` layout are unchanged either way, still one string field,
 * so an old row in the database (or an old client) reads a plain tile exactly as before.
 */
@Serializable
data class Tile(val letters: String, val value: Int) {
  init {
    require(letters.isNotEmpty()) { "A tile needs at least one letter, got an empty string" }
    val parts = letters.split('/')
    require(parts.all { part -> part.isNotEmpty() && part.all { it in 'A'..'Z' } }) {
      "Tile letters must be one or more normalized A-Z options separated by '/', got '$letters'"
    }
    require(value >= MinValue) { "Tile value must be at least $MinValue, got $value" }
  }

  /**
   * The alternative spellings this tile contributes to a path (ADR 0015): a plain tile or a digraph
   * like `QU` has exactly one option, equal to [letters]; an alternatives tile like `A/F` has one
   * option per side of the `/`.
   *
   * Lazily split and cached, not computed in [init]: the solver's DFS re-enters the same tile many
   * times over one board solve (a tile sits at the center of several paths), so re-splitting
   * [letters] on every visit would be wasted allocation on that hot path.
   */
  val options: List<String> by lazy { letters.split('/') }

  companion object {
    private const val MinValue = 1
  }
}
