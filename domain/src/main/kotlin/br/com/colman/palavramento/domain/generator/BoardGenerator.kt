// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.generator

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.mutator.Mutator
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

      val board = drawBoard(random, size)
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

  private fun drawBoard(random: Random, size: Int): Board {
    val tiles = List(size * size) {
      val letter = letterWeights.sample(random)
      Tile(letter.toString(), letterValues.value(letter))
    }
    return Board(size, tiles)
  }

  companion object {
    private const val DefaultSize = 4
    private const val MaxRelaxationRounds = 50
  }
}
