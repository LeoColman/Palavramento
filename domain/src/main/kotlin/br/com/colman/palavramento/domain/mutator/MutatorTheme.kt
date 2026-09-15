// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.mutator

/**
 * pt-BR header texts for a round's theme (dossier 1.5): a title naming the mutator and a subtitle
 * stating the generation restriction, e.g. "L de alto valor" / "19 palavras comuns". The server
 * sends both literally in `RoundStart` so the client never formats or translates them.
 */
object MutatorTheme {

  /** Mutator name shown as the first header line. */
  fun title(mutator: Mutator): String = when (mutator) {
    is Mutator.NoMutator -> "Grade padrão"
    is Mutator.ValuableLetter -> "${mutator.letter} de alto valor"
    is Mutator.Digraphs -> "Dígrafos"
    is Mutator.LetterInCorners -> "${mutator.letter} nos cantos"
  }

  /**
   * Grid restriction shown as the second header line: dossier 1.5 defines it as the generation
   * restriction, i.e. the minimum number of common words ([commonMin]) the grid was generated to
   * contain, not a property of the mutator itself.
   */
  fun subtitle(commonMin: Int): String = "$commonMin palavras comuns"
}
