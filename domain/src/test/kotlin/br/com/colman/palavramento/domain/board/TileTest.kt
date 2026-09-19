// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.board

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TileTest : FunSpec({
  test("Holds normalized letters and a value") {
    val tile = Tile("QU", 4)
    tile.letters shouldBe "QU"
    tile.value shouldBe 4
  }

  test("Rejects empty letters") {
    shouldThrow<IllegalArgumentException> { Tile("", 1) }
  }

  test("Rejects letters outside A-Z") {
    listOf("a", "Q1", "Ç", "L-").forEach { letters ->
      shouldThrow<IllegalArgumentException> { Tile(letters, 1) }
    }
  }

  test("Rejects a value below 1, accepts exactly 1") {
    shouldThrow<IllegalArgumentException> { Tile("A", 0) }
    Tile("A", 1).value shouldBe 1
  }

  test("Accepts both ends of the A-Z range") {
    Tile("A", 1).letters shouldBe "A"
    Tile("Z", 1).letters shouldBe "Z"
  }

  test("Accepts a digraph tile") {
    Tile("NH", 5).letters shouldBe "NH"
  }

  test("A plain tile has exactly one option, itself") {
    Tile("A", 1).options shouldBe listOf("A")
  }

  test("A digraph tile has exactly one option, the whole digraph") {
    Tile("QU", 4).options shouldBe listOf("QU")
  }

  test("An alternatives tile splits into one option per side of the slash (ADR 0015)") {
    Tile("A/F", 20).options shouldBe listOf("A", "F")
  }

  test("An alternatives tile can separate multi-letter options") {
    Tile("QU/CH", 10).options shouldBe listOf("QU", "CH")
  }

  test("Rejects malformed alternatives: leading, trailing or doubled slash") {
    listOf("/A", "A/", "A//F", "/").forEach { letters ->
      shouldThrow<IllegalArgumentException> { Tile(letters, 1) }
    }
  }

  test("Rejects an alternatives option outside A-Z") {
    listOf("a/F", "A/f", "A/1").forEach { letters ->
      shouldThrow<IllegalArgumentException> { Tile(letters, 1) }
    }
  }
})
