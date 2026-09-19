// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.board

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

// L O A R
// M I C T
// P V R I
// E O S M
private fun referenceBoard() = Board(
  4,
  listOf(
    Tile("L", 10), Tile("O", 2), Tile("A", 1), Tile("R", 2),
    Tile("M", 3), Tile("I", 2), Tile("C", 4), Tile("T", 4),
    Tile("P", 5), Tile("V", 6), Tile("R", 2), Tile("I", 2),
    Tile("E", 1), Tile("O", 2), Tile("S", 1), Tile("M", 3),
  ),
)

class PathTest : FunSpec({
  test("Spells the concatenation of tile letters") {
    Path(listOf(0, 5, 4, 1)).spell(referenceBoard()) shouldBe "LIMO"
  }

  test("A path of adjacent, distinct tiles is valid") {
    Path(listOf(0, 5, 4, 1)).isValidOn(referenceBoard()) shouldBe true
  }

  test("An empty path is invalid") {
    Path(emptyList()).isValidOn(referenceBoard()) shouldBe false
  }

  test("A path with an out-of-bounds index is invalid") {
    Path(listOf(0, 16)).isValidOn(referenceBoard()) shouldBe false
    Path(listOf(-1)).isValidOn(referenceBoard()) shouldBe false
  }

  test("A path that reuses a tile is invalid") {
    Path(listOf(0, 1, 0)).isValidOn(referenceBoard()) shouldBe false
  }

  test("A path with a non-adjacent step is invalid") {
    // 0 (L, row 0 col 0) and 11 (I, row 2 col 3) are far apart.
    Path(listOf(0, 11)).isValidOn(referenceBoard()) shouldBe false
  }

  test("A single tile is a valid, trivially adjacent path") {
    Path(listOf(0)).isValidOn(referenceBoard()) shouldBe true
    Path(listOf(0)).spell(referenceBoard()) shouldBe "L"
  }

  test("spellings of a path with no alternatives tile is a single-element list, the same as spell") {
    val board = referenceBoard()
    val path = Path(listOf(0, 5, 4, 1))
    path.spellings(board) shouldBe listOf(path.spell(board))
  }

  test("spellings is the cartesian product of the options along the path (ADR 0015)") {
    // C A/F T S: an alternatives tile at index 1.
    val board = Board(2, listOf(Tile("C", 3), Tile("A/F", 20), Tile("T", 3), Tile("S", 1)))
    Path(listOf(0, 1, 2)).spellings(board) shouldBe listOf("CAT", "CFT")
  }

  test("spellings with two alternatives tiles on the same path produces every combination, in order") {
    // A/F B/C X Y: a 2x2 board where every pair of tiles is adjacent.
    val board = Board(2, listOf(Tile("A/F", 20), Tile("B/C", 5), Tile("X", 1), Tile("Y", 1)))
    Path(listOf(0, 1)).spellings(board) shouldBe listOf("AB", "AC", "FB", "FC")
    // Sanity: a single-tile path still just enumerates that one tile's options.
    Path(listOf(0)).spellings(board) shouldBe listOf("A", "F")
  }
})
