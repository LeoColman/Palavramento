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
      val solution = solver.solve(board, commonCutoff)
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
   * Tiles carry the values players see and score (dossier 3, step 2: "aplicar mutador"): the solver,
   * the server's validation and the app all read each tile's own value, so every mutator is applied
   * here. That happens after the per-letter draw, with the same seeded [random], so the letter draw
   * always consumes the same sequence of [random] values regardless of mutator and generation stays
   * deterministic; only the placement step that follows differs.
   *
   * [Mutator.ValuableLetter] inflates exactly one tile of its letter (owner decision, 2026-09-15):
   * a copy picked at random or, when the draw produced none, a random tile turned into that letter,
   * so an "L de alto valor" round always has its L. [Mutator.Digraphs] and [Mutator.LetterInCorners]
   * (ADR 0012) replace some tiles outright.
   */
  private fun drawBoard(random: Random, size: Int, mutator: Mutator): Board {
    val tiles = MutableList(size * size) {
      val letter = letterWeights.sample(random)
      Tile(letter.toString(), letterValues.value(letter))
    }
    when (mutator) {
      is Mutator.ValuableLetter -> applyValuableLetter(random, tiles, mutator)
      is Mutator.Digraphs -> applyDigraphs(random, tiles, mutator.count)
      is Mutator.LetterInCorners -> applyCorners(tiles, size, mutator.letter)
      Mutator.NoMutator -> Unit
    }
    return Board(size, tiles)
  }

  /** Gives [mutator]'s value to a single tile of its letter, creating that tile when the draw had none. */
  private fun applyValuableLetter(random: Random, tiles: MutableList<Tile>, mutator: Mutator.ValuableLetter) {
    val letter = mutator.letter.toString()
    val copies = tiles.indices.filter { tiles[it].letters == letter }
    val position = if (copies.isEmpty()) random.nextInt(tiles.size) else copies[random.nextInt(copies.size)]
    tiles[position] = Tile(letter, mutator.value)
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
