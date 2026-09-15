// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.mutator

import br.com.colman.palavramento.domain.board.Tile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A round-wide rule that changes scoring or the win condition for a grid (dossier 1.5).
 *
 * Identifiers are English per project convention; the wire and save-file discriminators are the
 * exact tokens the dossier names ([SEM_MUTADOR], `LETRA_VALIOSA`, `LETRA_PROIBIDA`,
 * `TAMANHO_MINIMO`), carried by [SerialName] rather than by the Kotlin type name.
 *
 * `SO_SUBSTANTIVOS` (dossier 1.5) is explicitly out of scope for v1 and has no case here.
 */
@Serializable
sealed interface Mutator {

  /** The default grid: no letter value override, no forbidden letter, minimum length 3. */
  @Serializable
  @SerialName("SEM_MUTADOR")
  object NoMutator : Mutator

  /** [letter] scores [value] points per tile instead of its base value, e.g. L worth 10. */
  @Serializable
  @SerialName("LETRA_VALIOSA")
  data class ValuableLetter(val letter: Char, val value: Int) : Mutator {
    init {
      require(letter in 'A'..'Z') { "Mutator letter must be normalized A-Z, got '$letter'" }
      require(value >= MinValue) { "Mutator value must be at least $MinValue, got $value" }
    }

    private companion object {
      const val MinValue = 1
    }
  }

  /** Words containing [letter] are found but never score (dossier: shown greyed out). */
  @Serializable
  @SerialName("LETRA_PROIBIDA")
  data class ForbiddenLetter(val letter: Char) : Mutator {
    init {
      require(letter in 'A'..'Z') { "Mutator letter must be normalized A-Z, got '$letter'" }
    }
  }

  /** Minimum word length, in letters, rises from 3 to [length]. */
  @Serializable
  @SerialName("TAMANHO_MINIMO")
  data class MinimumLength(val length: Int) : Mutator {
    init {
      require(length >= MinLength) { "Minimum length must be at least $MinLength, got $length" }
    }

    private companion object {
      const val MinLength = 1
    }
  }
}

/** Dossier 1.1 default minimum word length, before any [Mutator.MinimumLength] override. */
const val DefaultMinimumLength = 3

/** Points a [tile] is worth under this mutator: [Mutator.ValuableLetter] overrides a single-letter tile. */
fun Mutator.effectiveValueOf(tile: Tile): Int =
  if (this is Mutator.ValuableLetter && tile.letters.length == 1 && tile.letters[0] == letter) {
    value
  } else {
    tile.value
  }

/** Minimum accepted word length in letters, honoring a [Mutator.MinimumLength] override. */
fun Mutator.minimumLength(): Int = if (this is Mutator.MinimumLength) length else DefaultMinimumLength

/** True when a normalized (A-Z) [word] cannot score because it contains a [Mutator.ForbiddenLetter]. */
fun Mutator.blocks(word: String): Boolean = this is Mutator.ForbiddenLetter && letter in word
