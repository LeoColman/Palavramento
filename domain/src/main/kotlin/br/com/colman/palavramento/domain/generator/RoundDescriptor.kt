// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.generator

import br.com.colman.palavramento.domain.mutator.Mutator
import kotlin.random.Random

/** A round's mutator plus the common-word minimum that becomes its theme subtitle (dossier 1.5). */
data class RoundDescriptor(val mutator: Mutator, val commonMin: Int)

/**
 * Deterministically picks a round's mutator and common-word minimum from a seed, so a room's
 * upcoming rounds can be scheduled ahead of time and reproduced for debugging (dossier 3: "mesma
 * seed, mesma grade" applies to the whole round, not just the letters).
 *
 * The exact odds and option pools here are a v1 starting point, not something the dossier pins
 * down: the orchestrator can retune [mutatorWeight], [commonMinOptions] etc. once real rounds show
 * which mutators feel good.
 */
object RoundDescriptorPicker {
  private val commonMinOptions = listOf(10, 15, 20, 25)
  private val valuableLetterValues = listOf(8, 10, 12)
  private val candidateLetters = "AEIOURSTNLMD".toList()

  /** Pool for [Mutator.LetterInCorners] (ADR 0012): dossier example is "O nos cantos". */
  private val cornerLetters = "AEIOSR".toList()

  /** [Mutator.Digraphs] draws between 2 and 4 digraph tiles (ADR 0012), inclusive. */
  private const val MinDigraphCount = 2
  private const val MaxDigraphCountExclusive = 5

  /** [Mutator.OneOrOther.first] pool (ADR 0015, owner example "A/F"): one of the four vowels. */
  private val vowels = "AEIO".toList()

  /**
   * [Mutator.OneOrOther.second] pool (ADR 0015): common pt-BR consonants, frequency-weighted by
   * `docs/calibracao-letras.md`'s "% ponderado" column (real-corpus letter usage), rounded to small
   * integer weights and expanded into that many repeated entries so a plain `.random(random)` draw is
   * weighted without a second cumulative-weight table just for ten letters:
   *
   * | Letter | Weight | Letter | Weight |
   * |---|---|---|---|
   * | S | 8 | C | 4 |
   * | R | 7 | D | 4 |
   * | N | 5 | L | 3 |
   * | M | 5 | P | 3 |
   * | T | 5 | F | 1 |
   */
  private val consonantPool: List<Char> = buildList {
    addWeighted('S', 8)
    addWeighted('R', 7)
    addWeighted('N', 5)
    addWeighted('M', 5)
    addWeighted('T', 5)
    addWeighted('C', 4)
    addWeighted('D', 4)
    addWeighted('L', 3)
    addWeighted('P', 3)
    addWeighted('F', 1)
  }

  private fun MutableList<Char>.addWeighted(letter: Char, weight: Int) = repeat(weight) { add(letter) }

  /** Number of equally likely mutator outcomes, one of which is [Mutator.NoMutator]. */
  private const val MutatorOutcomeCount = 5

  private const val NoMutatorOutcome = 0
  private const val ValuableLetterOutcome = 1
  private const val DigraphsOutcome = 2
  private const val LetterInCornersOutcome = 3
  // The fifth outcome, Mutator.OneOrOther, is the `else` branch below.

  fun pick(seed: Long): RoundDescriptor {
    val random = Random(seed)
    val mutator = when (random.nextInt(MutatorOutcomeCount)) {
      NoMutatorOutcome -> Mutator.NoMutator
      ValuableLetterOutcome ->
        Mutator.ValuableLetter(candidateLetters.random(random), valuableLetterValues.random(random))
      DigraphsOutcome -> Mutator.Digraphs(random.nextInt(MinDigraphCount, MaxDigraphCountExclusive))
      LetterInCornersOutcome -> Mutator.LetterInCorners(cornerLetters.random(random))
      else -> Mutator.OneOrOther(vowels.random(random), consonantPool.random(random))
    }
    val commonMin = commonMinOptions.random(random)
    return RoundDescriptor(mutator, commonMin)
  }
}
