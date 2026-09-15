// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.board

import kotlinx.serialization.Serializable

/**
 * One cell of a [Board]: one or more normalized letters (a digraph like `QU`, `AO`, `NH` or `LH`
 * consumes all of them at once, dossier 1.6) and the point value shown on the tile.
 *
 * [letters] is always uppercase A-Z, with diacritics already stripped by `WordNormalizer`: tiles
 * never carry accents, only the lexicon's display form does.
 */
@Serializable
data class Tile(val letters: String, val value: Int) {
  init {
    require(letters.isNotEmpty()) { "A tile needs at least one letter, got an empty string" }
    require(letters.all { it in 'A'..'Z' }) { "Tile letters must be normalized A-Z, got '$letters'" }
    require(value >= MinValue) { "Tile value must be at least $MinValue, got $value" }
  }

  companion object {
    private const val MinValue = 1
  }
}
