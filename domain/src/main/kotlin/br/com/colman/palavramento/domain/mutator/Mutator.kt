// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.mutator

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A round-wide rule that changes a grid's tiles or scoring (dossier 1.5, ADR 0012, ADR 0015).
 *
 * Identifiers are English per project convention; the wire and save-file discriminators are the
 * exact tokens the dossier names (`SEM_MUTADOR`, `LETRA_VALIOSA`, `DIGRAFOS`, `LETRA_NOS_CANTOS`,
 * `UMA_OU_OUTRA`), carried by [SerialName] rather than by the Kotlin type name.
 *
 * `LETRA_PROIBIDA` and `TAMANHO_MINIMO` existed in v1 and were removed by ADR 0012 (product owner
 * decision, 2026-09-15): every accepted word is at least [DefaultMinimumLength] letters now, with no
 * mutator override, and no letter ever blocks a word from scoring. `SO_SUBSTANTIVOS` (dossier 1.5) is
 * explicitly out of scope for v1 and has no case here either.
 */
@Serializable
sealed interface Mutator {

  /** The default grid: no letter value override, no structural change to the tiles. */
  @Serializable
  @SerialName("SEM_MUTADOR")
  object NoMutator : Mutator

  /**
   * One tile of [letter] is worth [value] instead of its base value, e.g. an L worth 10. Only one copy
   * (owner decision, 2026-09-15): the generator picks it and bakes the value into that tile, and
   * scoring always reads each tile's own value, so other copies of the letter keep their base value.
   */
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

  /**
   * The grid contains [count] digraph tiles, drawn from [br.com.colman.palavramento.domain.generator.DigraphTable]
   * (ADR 0012): a two-letter tile like `QU` or `CH` that the solver consumes in one step (dossier
   * 1.6). A digraph tile's value is the sum of its letters' base values, not looked up as a unit.
   */
  @Serializable
  @SerialName("DIGRAFOS")
  data class Digraphs(val count: Int) : Mutator {
    init {
      require(count >= MinCount) { "Digraph count must be at least $MinCount, got $count" }
    }

    private companion object {
      const val MinCount = 1
    }
  }

  /**
   * The four corner tiles (indices `0`, `size-1`, `size*(size-1)`, `size*size-1`) are [letter], at
   * its normal value (ADR 0012), e.g. "O nos cantos".
   */
  @Serializable
  @SerialName("LETRA_NOS_CANTOS")
  data class LetterInCorners(val letter: Char) : Mutator {
    init {
      require(letter in 'A'..'Z') { "Mutator letter must be normalized A-Z, got '$letter'" }
    }
  }

  /**
   * One tile reads `"$first/$second"`, e.g. `A/F` (ADR 0015, owner request 2026-09-18: "Uma letra
   * separada por / 'A/F' que vale 20 pontos e pode ser usada em palavras tanto com A quanto com F"):
   * a word can use that tile as either letter, and the same traced path can legitimately spell (and
   * score) a word through each option, one at a time. The generator places exactly one such tile, on
   * one of the board's non-edge positions, worth [br.com.colman.palavramento.domain.generator.BoardGenerator.OneOrOtherValue]
   * points regardless of which option a word uses.
   */
  @Serializable
  @SerialName("UMA_OU_OUTRA")
  data class OneOrOther(val first: Char, val second: Char) : Mutator {
    init {
      require(first in 'A'..'Z') { "Mutator letter must be normalized A-Z, got '$first'" }
      require(second in 'A'..'Z') { "Mutator letter must be normalized A-Z, got '$second'" }
    }
  }

  /**
   * A rule this build has no case for, which is what `PalavramentoJson` decodes an unrecognized
   * discriminator into (ADR 0018). The generator never produces it and no server ever sends it: it
   * only ever comes from a round created by a newer server, whose tiles already carry whatever the
   * rule did to them, so the grid stays playable under a name this build cannot state.
   */
  @Serializable
  @SerialName("DESCONHECIDO")
  object Unknown : Mutator
}

/** Dossier 1.1 minimum word length, in letters: always 3, no mutator overrides it (ADR 0012). */
const val DefaultMinimumLength = 3
