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

  test("One index past the last tile is out of bounds, the last tile itself is not") {
    // Single tile paths, so the bounds check is the only clause that can reject them: a two tile
    // path with an out-of-bounds index also fails the adjacency check, which hides the boundary.
    Path(listOf(16)).isValidOn(referenceBoard()) shouldBe false
    Path(listOf(15)).isValidOn(referenceBoard()) shouldBe true
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
})
