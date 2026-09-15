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
  private val minimumLengthOptions = listOf(4, 5, 6)
  private val valuableLetterValues = listOf(8, 10, 12)
  private val candidateLetters = "AEIOURSTNLMD".toList()

  /** Number of equally likely mutator outcomes, one of which is [Mutator.NoMutator]. */
  private const val MutatorOutcomeCount = 4

  fun pick(seed: Long): RoundDescriptor {
    val random = Random(seed)
    val mutator = when (random.nextInt(MutatorOutcomeCount)) {
      0 -> Mutator.NoMutator
      1 -> Mutator.ValuableLetter(candidateLetters.random(random), valuableLetterValues.random(random))
      2 -> Mutator.ForbiddenLetter(candidateLetters.random(random))
      else -> Mutator.MinimumLength(minimumLengthOptions.random(random))
    }
    val commonMin = commonMinOptions.random(random)
    return RoundDescriptor(mutator, commonMin)
  }
}
