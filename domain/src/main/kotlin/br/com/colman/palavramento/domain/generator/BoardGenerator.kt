// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.generator

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.mutator.effectiveValueOf
import br.com.colman.palavramento.domain.scoring.LetterValueTable
import br.com.colman.palavramento.domain.solver.Solver
import br.com.colman.palavramento.domain.solver.WordTier
import kotlin.random.Random

/**
 * Generates boards deterministically from a seed (dossier 3): draw letters weighted by pt-BR
 * frequency, apply the mutator, solve the grid, and accept it only if it meets [GenerationCriteria].
 * Otherwise redraw, relaxing the criteria every [GenerationCriteria.maxAttempts] failed attempts.
 *
 * `maxScore` and `totalWords` below are the same "theoretical maximum" the client shows as e.g.
 * "Points: 73/4193" (dossier 1.2): the sum and the count of every scorable word the solver finds,
 * not any single word's score.
 */
class BoardGenerator(
  private val solver: Solver,
  private val letterValues: LetterValueTable = LetterValueTable.default,
  private val letterWeights: LetterWeightTable = LetterWeightTable.default,
  private val digraphs: DigraphTable = DigraphTable.default,
) {

  fun generate(
    seed: Long,
    size: Int = DefaultSize,
    mutator: Mutator = Mutator.NoMutator,
    commonCutoff: Int = Solver.DefaultCommonCutoff,
    criteria: GenerationCriteria = GenerationCriteria(),
  ): GenerationResult {
    val random = Random(seed)
    var activeCriteria = criteria
    var attempts = 0
    var attemptsInBlock = 0
    // Safety valve so a misconfigured or pathological lexicon cannot spin forever: relaxation
    // alone is proven to terminate (see GenerationCriteria.relaxed), but this bounds it anyway.
    val hardCap = criteria.maxAttempts.toLong() * MaxRelaxationRounds

    while (attempts < hardCap) {
      attempts++
      attemptsInBlock++

      val board = drawBoard(random, size, mutator)
      val solution = solver.solve(board, mutator, commonCutoff)
      val commonWords = solution.count { it.tier == WordTier.Common }
      val totalWords = solution.size
      val maxScore = solution.sumOf { it.score }

      val accepted = commonWords >= activeCriteria.commonMin &&
        totalWords >= activeCriteria.totalWordsMin &&
        maxScore in activeCriteria.maxScoreRange
      if (accepted) {
        return GenerationResult(board, solution, seed, activeCriteria, attempts)
      }

      if (attemptsInBlock >= criteria.maxAttempts) {
        activeCriteria = activeCriteria.relaxed()
        attemptsInBlock = 0
      }
    }
    error("Board generation gave up after $attempts attempts (seed=$seed, size=$size)")
  }

  /**
   * Tiles carry the values players see (dossier 3, step 2: "aplicar mutador"), so a
   * [Mutator.ValuableLetter] is baked in here rather than only applied inside the solver. The
   * override is idempotent, so the solver applying it again changes nothing.
   *
   * [Mutator.Digraphs] and [Mutator.LetterInCorners] (ADR 0012) are structural: they replace some of
   * the drawn tiles outright, after the per-letter draw above, using the same seeded [random] so
   * generation stays deterministic. Applying them second (never first) means the letter draw always
   * consumes the same sequence of [random] values regardless of mutator, and only the placement step
   * that follows differs.
   */
  private fun drawBoard(random: Random, size: Int, mutator: Mutator): Board {
    val tiles = MutableList(size * size) {
      val letter = letterWeights.sample(random)
      val base = Tile(letter.toString(), letterValues.value(letter))
      Tile(base.letters, mutator.effectiveValueOf(base))
    }
    when (mutator) {
      is Mutator.Digraphs -> applyDigraphs(random, tiles, mutator.count)
      is Mutator.LetterInCorners -> applyCorners(tiles, size, mutator.letter)
      else -> Unit
    }
    return Board(size, tiles)
  }

  /** Overwrites [count] distinct, randomly chosen tiles with digraph tiles drawn from [digraphs]. */
  private fun applyDigraphs(random: Random, tiles: MutableList<Tile>, count: Int) {
    val positions = tiles.indices.shuffled(random).take(count)
    for (position in positions) {
      val letters = digraphs.sample(random)
      val value = letters.sumOf { letterValues.value(it) }
      tiles[position] = Tile(letters, value)
    }
  }

  /** Overwrites the four corner tiles of a [size]x[size] board with [letter], at its normal value. */
  private fun applyCorners(tiles: MutableList<Tile>, size: Int, letter: Char) {
    val value = letterValues.value(letter)
    for (corner in cornersOf(size)) {
      tiles[corner] = Tile(letter.toString(), value)
    }
  }

  companion object {
    private const val DefaultSize = 4
    private const val MaxRelaxationRounds = 50
  }
}

/** The four corner indices of a [size]x[size] row-major board (dossier 1.1 layout, ADR 0012). */
internal fun cornersOf(size: Int): List<Int> = listOf(0, size - 1, size * (size - 1), size * size - 1)
