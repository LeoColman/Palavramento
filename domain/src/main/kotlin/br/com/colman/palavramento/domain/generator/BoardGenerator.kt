// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.generator

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.scoring.LetterValueTable
import br.com.colman.palavramento.domain.solver.SolvedWord
import br.com.colman.palavramento.domain.solver.Solver
import br.com.colman.palavramento.domain.solver.WordTier
import kotlin.random.Random

/**
 * Generates boards deterministically from a seed (dossier 3): draw letters weighted by pt-BR
 * frequency, apply the mutator, solve the grid, and accept it only if it meets [GenerationCriteria]
 * (which since ADR 0015 also covers [SpecialTileCriteria], how well a mutator's own special tiles are
 * actually used by the solution). Otherwise redraw, relaxing the criteria every
 * [GenerationCriteria.maxAttempts] failed attempts.
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

      val draw = drawBoard(random, size, mutator)
      val solution = solver.solve(draw.board, commonCutoff)
      val commonWords = solution.count { it.tier == WordTier.Common }
      val totalWords = solution.size
      val maxScore = solution.sumOf { it.score }

      val accepted = commonWords >= activeCriteria.commonMin &&
        totalWords >= activeCriteria.totalWordsMin &&
        maxScore in activeCriteria.maxScoreRange &&
        satisfiesSpecialTiles(mutator, draw, solution, activeCriteria.specialTiles)
      if (accepted) {
        return GenerationResult(draw.board, solution, seed, activeCriteria, attempts)
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
   * (ADR 0012), and [Mutator.OneOrOther] (ADR 0015), replace some tiles outright.
   *
   * Returns the positions the mutator actually wrote, alongside the board, so [satisfiesSpecialTiles]
   * can check the solution against those exact tiles without having to re-derive them by scanning.
   */
  private fun drawBoard(random: Random, size: Int, mutator: Mutator): BoardDraw {
    val tiles = MutableList(size * size) {
      val letter = letterWeights.sample(random)
      Tile(letter.toString(), letterValues.value(letter))
    }
    val specialPositions = when (mutator) {
      is Mutator.ValuableLetter -> listOf(applyValuableLetter(random, tiles, mutator))
      is Mutator.Digraphs -> applyDigraphs(random, tiles, size, mutator.count)
      is Mutator.LetterInCorners -> applyCorners(tiles, size, mutator.letter)
      is Mutator.OneOrOther -> listOf(applyOneOrOther(random, tiles, size, mutator))
      Mutator.NoMutator -> emptyList()
    }
    return BoardDraw(Board(size, tiles), specialPositions)
  }

  /** Gives [mutator]'s value to a single tile of its letter, creating that tile when the draw had none. */
  private fun applyValuableLetter(random: Random, tiles: MutableList<Tile>, mutator: Mutator.ValuableLetter): Int {
    val letter = mutator.letter.toString()
    val copies = tiles.indices.filter { tiles[it].letters == letter }
    val position = if (copies.isEmpty()) random.nextInt(tiles.size) else copies[random.nextInt(copies.size)]
    tiles[position] = Tile(letter, mutator.value)
    return position
  }

  /**
   * Overwrites [count] digraph tiles drawn from [digraphs], placed so every one of them can actually
   * be reached by a word (owner request 2026-09-18): never on a corner (a corner tile has only 3
   * neighbors, half the plain 8, so it is the hardest position on the board to spell through) and
   * never adjacent to another digraph tile (two digraphs touching starve each other of the plain,
   * vowel-friendly neighbors a word needs to enter or leave them). See [digraphPositions].
   */
  private fun applyDigraphs(random: Random, tiles: MutableList<Tile>, size: Int, count: Int): List<Int> {
    val positions = digraphPositions(random, size, count)
    for (position in positions) {
      val letters = digraphs.sample(random)
      val value = letters.sumOf { letterValues.value(it) }
      tiles[position] = Tile(letters, value)
    }
    return positions
  }

  /** Overwrites the four corner tiles of a [size]x[size] board with [letter], at its normal value. */
  private fun applyCorners(tiles: MutableList<Tile>, size: Int, letter: Char): List<Int> {
    val value = letterValues.value(letter)
    val corners = cornersOf(size)
    for (corner in corners) {
      tiles[corner] = Tile(letter.toString(), value)
    }
    return corners
  }

  /**
   * Places the single `first/second` alternatives tile (ADR 0015) worth [OneOrOtherValue] points on
   * one of the board's non-edge positions (the four center cells of the shipped 4x4 board, the only
   * ones with all 8 neighbors), chosen by the seeded [random] so placement stays deterministic. Falls
   * back to any position when the board is too small to have a non-edge cell at all (a pathological
   * size only property tests exercise; the shipped 4x4 board always has the four).
   */
  private fun applyOneOrOther(random: Random, tiles: MutableList<Tile>, size: Int, mutator: Mutator.OneOrOther): Int {
    val candidates = centersOf(size).ifEmpty { tiles.indices.toList() }
    val position = candidates[random.nextInt(candidates.size)]
    tiles[position] = Tile("${mutator.first}/${mutator.second}", OneOrOtherValue)
    return position
  }

  /**
   * Whether [draw]'s special tiles (whichever [mutator] placed) are actually woven into [solution],
   * per [thresholds] (owner request 2026-09-18, [SpecialTileCriteria]). Always true for
   * [Mutator.NoMutator], which places no special tile to check.
   */
  private fun satisfiesSpecialTiles(
    mutator: Mutator,
    draw: BoardDraw,
    solution: List<SolvedWord>,
    thresholds: SpecialTileCriteria,
  ): Boolean = when (mutator) {
    Mutator.NoMutator -> true

    is Mutator.ValuableLetter ->
      wordsUsing(draw.specialPositions.single(), solution).size >= thresholds.minWordsPerTile

    is Mutator.Digraphs -> draw.specialPositions.all { position ->
      val words = wordsUsing(position, solution)
      // The "at least one Common" half is vacuously satisfied once relaxation has driven
      // minWordsPerTile down to 0 and this particular tile still has no word at all: otherwise
      // `words.any { }` on an empty list is always false and this criterion could never relax to
      // satisfiable, breaking GenerationCriteria's "repeated failures eventually make acceptance
      // certain" guarantee (docs/adr/0015).
      words.size >= thresholds.minWordsPerTile && (words.isEmpty() || words.any { it.tier == WordTier.Common })
    }

    is Mutator.LetterInCorners -> draw.specialPositions.all { corner ->
      wordsUsing(corner, solution).size >= thresholds.minWordsPerCorner
    }

    is Mutator.OneOrOther -> {
      val position = draw.specialPositions.single()
      val words = wordsUsing(position, solution)
      val firstCount = words.count { optionUsedAt(draw.board, it, position) == mutator.first }
      val secondCount = words.count { optionUsedAt(draw.board, it, position) == mutator.second }
      words.size >= thresholds.minWordsPerTile &&
        firstCount >= thresholds.minWordsPerOption &&
        secondCount >= thresholds.minWordsPerOption
    }
  }

  companion object {
    private const val DefaultSize = 4
    private const val MaxRelaxationRounds = 50

    /** Point value of the [Mutator.OneOrOther] tile, regardless of which option a word uses (ADR 0015). */
    const val OneOrOtherValue = 20
  }
}

/** A drawn board together with the tile positions its mutator wrote, if any. */
private data class BoardDraw(val board: Board, val specialPositions: List<Int>)

/** The four corner indices of a [size]x[size] row-major board (dossier 1.1 layout, ADR 0012). */
internal fun cornersOf(size: Int): List<Int> = listOf(0, size - 1, size * (size - 1), size * size - 1)

/**
 * The board's non-edge indices: for a 4x4 board, the four center cells with all 8 neighbors
 * (`row`/`col` both in `1 until size - 1`); for a smaller board, whatever cell(s) qualify the same
 * way (a 3x3 board has exactly one, its very center). Empty for `size <= 2`, where every cell touches
 * an edge.
 */
internal fun centersOf(size: Int): List<Int> {
  val nonEdge = 1 until (size - 1)
  return nonEdge.flatMap { row -> nonEdge.map { col -> row * size + col } }
}

/** True when [a] and [b] are different cells of a [size]x[size] board within 8-direction reach of each other. */
private fun areAdjacent(size: Int, a: Int, b: Int): Boolean {
  if (a == b) return false
  val rowDelta = a / size - b / size
  val colDelta = a % size - b % size
  return rowDelta in -1..1 && colDelta in -1..1
}

/**
 * [count] distinct non-corner positions of a [size]x[size] board, no two adjacent to each other
 * (owner request: digraph tiles must be reachable, ADR 0012 update). [random] shuffles the candidate
 * order first, so which valid combination comes out is still seeded and deterministic; a real
 * backtracking search over that order (not a greedy one) is used because a greedy pick can paint
 * itself into a corner (pun intended) and miss a combination that does exist: the 4x4 board's max
 * mutually-non-adjacent, non-corner set is 4 (docs/adr/0015), but not every rectangular pattern of 4
 * reaches it, and a naive greedy walk can dead-end at 3 when a valid 4th position was available all
 * along, just not adjacent to the greedy path's particular choices.
 */
private fun digraphPositions(random: Random, size: Int, count: Int): List<Int> {
  if (count == 0) return emptyList()
  val corners = cornersOf(size).toSet()
  val candidates = (0 until size * size).filterNot { it in corners }.shuffled(random)
  return findNonAdjacentSubset(candidates, count, size) ?: candidates.take(count)
}

/**
 * Backtracking search for [count] entries of [candidates], in that order, no two mutually adjacent
 * (via [areAdjacent]). Returns null only when no such subset exists at all among [candidates].
 *
 * Written as a pure recursive function over persistent lists rather than a mutable
 * add-then-undo loop: [count] is at most 4, so the extra small-list allocations are cheap, and the
 * short-circuiting `firstNotNullOfOrNull` over a lazy sequence keeps this to two return statements.
 */
private fun findNonAdjacentSubset(candidates: List<Int>, count: Int, size: Int): List<Int>? {
  fun backtrack(startIndex: Int, chosen: List<Int>): List<Int>? {
    if (chosen.size == count) return chosen
    return (startIndex until candidates.size).asSequence()
      .filter { index -> chosen.none { areAdjacent(size, it, candidates[index]) } }
      .firstNotNullOfOrNull { index -> backtrack(index + 1, chosen + candidates[index]) }
  }

  return backtrack(0, emptyList())
}

/** Solved words whose path passes through [position]. */
private fun wordsUsing(position: Int, solution: List<SolvedWord>): List<SolvedWord> =
  solution.filter { position in it.path }

/**
 * Which option of the tile at [position] a solved [word] used, read back out of [word]'s own
 * [SolvedWord.normalized] form. Every tile on the board contributes a fixed number of letters to a
 * word's spelling regardless of which option is chosen - a digraph always contributes 2, a plain tile
 * always 1, and [Mutator.OneOrOther]'s two options are always single characters (dossier: the owner's
 * example is a single letter each side of the `/`) - so the character offset of [position] within
 * [word]'s spelling can be computed from the board alone, without the solver having to record it.
 */
private fun optionUsedAt(board: Board, word: SolvedWord, position: Int): Char {
  val indexInPath = word.path.indexOf(position)
  val offset = word.path.take(indexInPath).sumOf { board.tiles[it].options.first().length }
  return word.normalized[offset]
}
