// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.game

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Path
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.board.isValidOn
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlin.random.Random

// A uniform board: only adjacency matters for the tracer, never letters or values.
private fun referenceBoard(): Board = Board(4, List(16) { Tile("A", 1) })

/** Builds an in-tracer path by repeatedly stepping to an unused neighbor. False if it got stuck. */
private fun buildValidPath(tracer: PathTracer, board: Board, random: Random, minLength: Int): Boolean {
  tracer.begin(random.nextInt(board.tiles.size))
  while (tracer.path.size < minLength) {
    val last = tracer.path.last()
    val candidates = board.neighborsOf(last).filter { it !in tracer.path }
    if (candidates.isEmpty()) return false
    tracer.onTileEntered(candidates[random.nextInt(candidates.size)])
  }
  return true
}

class PathTracerTest : FunSpec({

  test("begin() starts a single-tile path") {
    val tracer = PathTracer(referenceBoard())
    tracer.begin(5)
    tracer.path shouldBe listOf(5)
  }

  test("The first tile entered on an empty tracer starts the path") {
    val tracer = PathTracer(referenceBoard())
    tracer.onTileEntered(3) shouldBe true
    tracer.path shouldBe listOf(3)
  }

  test("An adjacent, unused tile is appended") {
    val tracer = PathTracer(referenceBoard())
    tracer.begin(0)
    // Board is row-major 4x4: 0 = (0,0), 1 = (0,1) is adjacent to 0.
    tracer.onTileEntered(1) shouldBe true
    tracer.path shouldBe listOf(0, 1)
  }

  test("A non-adjacent tile is ignored") {
    val tracer = PathTracer(referenceBoard())
    tracer.begin(0)
    // 15 = (3,3) is far from 0 = (0,0): not adjacent.
    tracer.onTileEntered(15) shouldBe false
    tracer.path shouldBe listOf(0)
  }

  test("Re-entering the current last tile is a no-op") {
    val tracer = PathTracer(referenceBoard())
    tracer.begin(0)
    tracer.onTileEntered(0) shouldBe false
    tracer.path shouldBe listOf(0)
  }

  test("Entering the second-to-last tile undoes the last append (natural backtrack)") {
    val tracer = PathTracer(referenceBoard())
    tracer.begin(0)
    tracer.onTileEntered(1)
    tracer.onTileEntered(2)
    tracer.path shouldBe listOf(0, 1, 2)

    tracer.onTileEntered(1) shouldBe true
    tracer.path shouldBe listOf(0, 1)
  }

  test("A tile already in the path, other than the second-to-last, is ignored") {
    val tracer = PathTracer(referenceBoard())
    tracer.begin(0)
    tracer.onTileEntered(1)
    tracer.onTileEntered(2)
    tracer.path shouldBe listOf(0, 1, 2)

    // 0 is used, but it is not the second-to-last tile (1 is): ignored, not an undo.
    tracer.onTileEntered(0) shouldBe false
    tracer.path shouldBe listOf(0, 1, 2)
  }

  test("clear() empties the path") {
    val tracer = PathTracer(referenceBoard())
    tracer.begin(0)
    tracer.onTileEntered(1)
    tracer.clear()
    tracer.path shouldBe emptyList()
  }

  test("PROPERTY: after any sequence of entries, the path is always adjacency-valid and repeat-free") {
    val board = referenceBoard()
    checkAll(Arb.list(Arb.int(0..15), 0..80)) { moves ->
      val tracer = PathTracer(board)
      for (move in moves) {
        tracer.onTileEntered(move)
        if (tracer.path.isNotEmpty()) {
          Path(tracer.path).isValidOn(board) shouldBe true
        }
      }
    }
  }

  test("PROPERTY: entering the second-to-last tile of any built path shrinks it by exactly one") {
    val board = referenceBoard()
    checkAll(Arb.long()) { seed ->
      val random = Random(seed)
      val tracer = PathTracer(board)
      val built = buildValidPath(tracer, board, random, minLength = 2)
      if (built) {
        val before = tracer.path
        val secondToLast = before[before.size - 2]
        tracer.onTileEntered(secondToLast) shouldBe true
        tracer.path shouldBe before.dropLast(1)
      }
    }
  }
})
