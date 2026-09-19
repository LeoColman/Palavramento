// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.generator.BoardGenerator
import br.com.colman.palavramento.domain.generator.GenerationResult
import br.com.colman.palavramento.domain.generator.LetterWeightTable
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.scoring.LetterValueTable
import br.com.colman.palavramento.domain.solver.SolvedWord
import br.com.colman.palavramento.domain.solver.Solver
import br.com.colman.palavramento.domain.solver.WordTier
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeBetween
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

private const val NanosPerMilli = 1_000_000.0
private const val CalibrationSeedCount = 10L

/** Boards drawn exactly like the generator draws them, without its acceptance filter. */
private fun drawBoards(count: Int, seed: Int = 1): List<Board> {
  val random = Random(seed)
  val weights = LetterWeightTable.default
  val values = LetterValueTable.default
  return List(count) {
    Board(4, List(16) { weights.sample(random).let { letter -> Tile(letter.toString(), values.value(letter)) } })
  }
}

private fun <T : Comparable<T>> List<T>.percentile(fraction: Double): T = sorted()[((size - 1) * fraction).toInt()]

/** The four corner indices of a [size]x[size] row-major board (test-local re-derivation of
 * BoardGenerator's own internal `cornersOf`, not visible outside `:domain`). */
private fun cornersOf(size: Int): List<Int> = listOf(0, size - 1, size * (size - 1), size * size - 1)

/** Solved words whose path passes through [position] (test-local re-derivation of BoardGenerator's own
 * private `wordsUsing`, ADR 0015). */
private fun wordsUsingTile(position: Int, solution: List<SolvedWord>): List<SolvedWord> =
  solution.filter { position in it.path }

/** Which option of the tile at [position] a solved [word] used (test-local re-derivation of
 * BoardGenerator's own private `optionUsedAt`, ADR 0015). */
private fun optionUsedAt(board: Board, word: SolvedWord, position: Int): Char {
  val indexInPath = word.path.indexOf(position)
  val offset = word.path.take(indexInPath).sumOf { board.tiles[it].options.first().length }
  return word.normalized[offset]
}

/** True when [a] and [b] are within 8-direction reach of each other on a [size]x[size] board (test-local
 * re-derivation of BoardGenerator's own private `areAdjacent`, ADR 0012/0015). */
private fun adjacent(size: Int, a: Int, b: Int): Boolean {
  if (a == b) return false
  val rowDelta = a / size - b / size
  val colDelta = a % size - b % size
  return rowDelta in -1..1 && colDelta in -1..1
}

/** Runs [seeds] generations of [mutator], asserting the dossier's base criteria plus [check] (the
 * mutator's own special-tile assertions), and prints attempts/board and ms/board for the orchestrator. */
private fun calibrate(
  label: String,
  mutator: Mutator,
  seeds: LongRange = 1L..CalibrationSeedCount,
  check: (GenerationResult) -> Unit = {},
) {
  val generator = BoardGenerator(Solver(LexiconLoader.load()))
  var totalAttempts = 0
  val millis = seeds.map { seed ->
    lateinit var result: GenerationResult
    val elapsed = measureTimeMillis { result = generator.generate(seed = seed, mutator = mutator) }
    totalAttempts += result.attempts
    val criteria = result.criteriaUsed
    (result.solution.count { it.tier == WordTier.Common } >= criteria.commonMin) shouldBe true
    (result.solution.size >= criteria.totalWordsMin) shouldBe true
    (result.solution.sumOf { it.score } in criteria.maxScoreRange) shouldBe true
    check(result)
    elapsed
  }
  val seedCount = seeds.count()
  println(
    "$label: avg ${totalAttempts / seedCount} attempts/board, " +
      "avg ${millis.average().toInt()} ms/board, total ${millis.sum()} ms for $seedCount boards",
  )
}

/**
 * Guards the calibration recorded in docs/calibracao-letras.md against the real lexicon: solver speed
 * (dossier 2.4), board acceptance (dossier 3) and the common/expert split (dossier 1.7). Since the
 * owner's "vamos tornar esses jogos um pouco mais divertidos" request (docs/adr/0015), also guards
 * that every mode with a special tile actually weaves it into the solution, for all five mutators.
 */
class RealLexiconCalibrationTest : FunSpec({
  test("A full 4x4 solve takes under 50 ms with the real lexicon") {
    val solver = Solver(LexiconLoader.load())
    val boards = drawBoards(300)
    boards.take(50).forEach { solver.solve(it) } // JIT warm-up
    val millis = boards.map { board -> measureNanoTime { solver.solve(board) } / NanosPerMilli }
    // p99 rather than max, so one GC pause on a shared CI runner cannot fail the build.
    millis.percentile(0.99) shouldBeLessThan 50.0
  }

  test("Default generation settles on boards that meet the dossier's criteria") {
    calibrate("NoMutator", Mutator.NoMutator)
  }

  test("Common and expert words split close to 1:1 on typical boards") {
    val solver = Solver(LexiconLoader.load())
    val shares = drawBoards(300, seed = 2).map { board ->
      val solution = solver.solve(board)
      solution.count { it.tier == WordTier.Common }.toDouble() / maxOf(1, solution.size)
    }
    shares.percentile(0.5).shouldBeBetween(0.4, 0.6, 0.0)
  }

  // ADR 0012/0015: mutators that place a special tile could plausibly starve it of any solved word
  // (a digraph tile in a corner with no vowel around it, four corners locked to an unreachable letter,
  // the one inflated letter tucked away, the alternatives tile never combining into a second word).
  // These checks prove that is not the case with the real lexicon, and report attempts/time per board.
  test("ValuableLetter generation settles on boards that weave the inflated tile into the solution") {
    calibrate("ValuableLetter('L', 10)", Mutator.ValuableLetter('L', 10)) { result ->
      val position = result.board.tiles.indexOfFirst { it.letters == "L" && it.value == 10 }
      val words = wordsUsingTile(position, result.solution)
      (words.size >= result.criteriaUsed.specialTiles.minWordsPerTile) shouldBe true
    }
  }

  test("Digraphs generation settles on boards that meet the dossier's criteria, with the real lexicon") {
    calibrate("Digraphs(3)", Mutator.Digraphs(3)) { result ->
      result.board.tiles.count { it.letters.length == 2 } shouldBe 3
      val digraphPositions = result.board.tiles.indices.filter { result.board.tiles[it].letters.length == 2 }
      val corners = cornersOf(result.board.size).toSet()
      digraphPositions.forEach { position ->
        (position in corners) shouldBe false
        val words = wordsUsingTile(position, result.solution)
        (words.size >= result.criteriaUsed.specialTiles.minWordsPerTile) shouldBe true
        (words.any { it.tier == WordTier.Common }) shouldBe true
      }
      digraphPositions.forEachIndexed { index, a ->
        digraphPositions.drop(index + 1).forEach { b -> adjacent(result.board.size, a, b) shouldBe false }
      }
    }
  }

  test("LetterInCorners generation settles on boards that meet the dossier's criteria, with the real lexicon") {
    calibrate("LetterInCorners('O')", Mutator.LetterInCorners('O')) { result ->
      val size = result.board.size
      cornersOf(size).forEach { corner ->
        result.board.tiles[corner].letters shouldBe "O"
        val words = wordsUsingTile(corner, result.solution)
        (words.size >= result.criteriaUsed.specialTiles.minWordsPerCorner) shouldBe true
      }
    }
  }

  test("OneOrOther generation settles on boards that meet the dossier's criteria, with the real lexicon") {
    val mutator = Mutator.OneOrOther('A', 'F')
    calibrate("OneOrOther('A', 'F')", mutator) { result ->
      val alternatives = result.board.tiles.filter { it.letters.contains('/') }
      alternatives.size shouldBe 1
      val tile = alternatives.single()
      tile.letters shouldBe "A/F"
      tile.value shouldBe BoardGenerator.OneOrOtherValue
      val position = result.board.tiles.indexOf(tile)
      val words = wordsUsingTile(position, result.solution)
      val firstCount = words.count { optionUsedAt(result.board, it, position) == 'A' }
      val secondCount = words.count { optionUsedAt(result.board, it, position) == 'F' }
      (words.size >= result.criteriaUsed.specialTiles.minWordsPerTile) shouldBe true
      (firstCount >= result.criteriaUsed.specialTiles.minWordsPerOption) shouldBe true
      (secondCount >= result.criteriaUsed.specialTiles.minWordsPerOption) shouldBe true
    }
  }
})
