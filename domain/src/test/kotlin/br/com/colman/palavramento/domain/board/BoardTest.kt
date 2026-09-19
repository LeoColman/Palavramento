// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.board

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun tile(letter: Char) = Tile(letter.toString(), 1)

private fun square(size: Int) = Board(size, List(size * size) { tile('A' + (it % 26)) })

class BoardTest : FunSpec({
  test("size is the grid side, not the tile count") {
    val board = square(4)
    board.size shouldBe 4
    board.tiles.size shouldBe 16
  }

  test("Neighbors come back in ascending index order") {
    // The solver walks this array for every DFS step, so a stable order is what makes solving the
    // same board twice produce the same word order.
    val board = square(4)
    for (index in board.tiles.indices) {
      val neighbors = board.neighborsOf(index).toList()
      neighbors shouldBe neighbors.sorted()
    }
  }

  test("Index is row-major") {
    val board = square(4)
    board.indexOf(0, 0) shouldBe 0
    board.indexOf(1, 0) shouldBe 4
    board.indexOf(0, 1) shouldBe 1
    board.indexOf(3, 3) shouldBe 15
  }

  test("rowOf and colOf invert indexOf") {
    val board = square(4)
    for (index in board.tiles.indices) {
      board.indexOf(board.rowOf(index), board.colOf(index)) shouldBe index
    }
  }

  test("Rejects a tile count that does not match size squared, naming the expected count") {
    val exception = shouldThrow<IllegalArgumentException> { Board(4, listOf(tile('A'))) }
    exception.message shouldContain "16 tiles"
  }

  test("Rejects a non-positive size") {
    shouldThrow<IllegalArgumentException> { Board(0, emptyList()) }
  }

  test("A corner tile has 3 neighbors, 8 directions") {
    val board = square(4)
    board.neighborsOf(0).toList() shouldContainExactlyInAnyOrder listOf(1, 4, 5)
  }

  test("An edge tile has 5 neighbors") {
    val board = square(4)
    // Index 1 is (row 0, col 1): top edge, not a corner.
    board.neighborsOf(1).toList() shouldContainExactlyInAnyOrder listOf(0, 2, 4, 5, 6)
  }

  test("An interior tile has all 8 neighbors") {
    val board = square(4)
    // Index 5 is (row 1, col 1): interior on a 4x4 board.
    board.neighborsOf(5).toList() shouldContainExactlyInAnyOrder listOf(0, 1, 2, 4, 6, 8, 9, 10)
  }

  test("Adjacency is symmetric") {
    val board = square(4)
    for (a in board.tiles.indices) {
      for (b in board.tiles.indices) {
        board.areAdjacent(a, b) shouldBe board.areAdjacent(b, a)
      }
    }
  }

  test("A tile is never its own neighbor") {
    val board = square(4)
    for (index in board.tiles.indices) {
      board.neighborsOf(index).toList() shouldNotContain index
    }
  }
})
