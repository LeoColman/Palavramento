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

/**
 * Guards the calibration recorded in docs/calibracao-letras.md against the real lexicon: solver speed
 * (dossier 2.4), board acceptance (dossier 3) and the common/expert split (dossier 1.7).
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
    val generator = BoardGenerator(Solver(LexiconLoader.load()))
    (1L..20L).forEach { seed ->
      val result = generator.generate(seed)
      val criteria = result.criteriaUsed
      (result.solution.count { it.tier == WordTier.Common } >= criteria.commonMin) shouldBe true
      (result.solution.size >= criteria.totalWordsMin) shouldBe true
      (result.solution.sumOf { it.score } in criteria.maxScoreRange) shouldBe true
    }
  }

  test("Common and expert words split close to 1:1 on typical boards") {
    val solver = Solver(LexiconLoader.load())
    val shares = drawBoards(300, seed = 2).map { board ->
      val solution = solver.solve(board)
      solution.count { it.tier == WordTier.Common }.toDouble() / maxOf(1, solution.size)
    }
    shares.percentile(0.5).shouldBeBetween(0.4, 0.6, 0.0)
  }

  // ADR 0012: the two new structural mutators overwrite tiles after the normal letter draw, which
  // could plausibly starve a board of common words (a digraph tile that never participates in any
  // lexicon word, or four corners locked to one letter). These checks prove that is not the case
  // with the real lexicon, and report attempts/time per board for the orchestrator.
  test("Digraphs generation settles on boards that meet the dossier's criteria, with the real lexicon") {
    val generator = BoardGenerator(Solver(LexiconLoader.load()))
    var totalAttempts = 0
    val millis = (1L..10L).map { seed ->
      lateinit var result: GenerationResult
      val elapsed = measureTimeMillis {
        result = generator.generate(seed = seed, mutator = Mutator.Digraphs(3))
      }
      totalAttempts += result.attempts
      val criteria = result.criteriaUsed
      (result.solution.count { it.tier == WordTier.Common } >= criteria.commonMin) shouldBe true
      (result.solution.size >= criteria.totalWordsMin) shouldBe true
      (result.solution.sumOf { it.score } in criteria.maxScoreRange) shouldBe true
      result.board.tiles.count { it.letters.length == 2 } shouldBe 3
      elapsed
    }
    println(
      "Digraphs(3): avg ${totalAttempts / 10} attempts/board, " +
        "avg ${millis.average().toInt()} ms/board, total ${millis.sum()} ms for 10 boards",
    )
  }

  test("LetterInCorners generation settles on boards that meet the dossier's criteria, with the real lexicon") {
    val generator = BoardGenerator(Solver(LexiconLoader.load()))
    var totalAttempts = 0
    val millis = (1L..10L).map { seed ->
      lateinit var result: GenerationResult
      val elapsed = measureTimeMillis {
        result = generator.generate(seed = seed, mutator = Mutator.LetterInCorners('O'))
      }
      totalAttempts += result.attempts
      val criteria = result.criteriaUsed
      (result.solution.count { it.tier == WordTier.Common } >= criteria.commonMin) shouldBe true
      (result.solution.size >= criteria.totalWordsMin) shouldBe true
      (result.solution.sumOf { it.score } in criteria.maxScoreRange) shouldBe true
      val size = result.board.size
      listOf(0, size - 1, size * (size - 1), size * size - 1).forEach { corner ->
        result.board.tiles[corner].letters shouldBe "O"
      }
      elapsed
    }
    println(
      "LetterInCorners('O'): avg ${totalAttempts / 10} attempts/board, " +
        "avg ${millis.average().toInt()} ms/board, total ${millis.sum()} ms for 10 boards",
    )
  }
})
