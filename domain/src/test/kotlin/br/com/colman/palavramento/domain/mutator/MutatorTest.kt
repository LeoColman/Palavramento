// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.mutator

import br.com.colman.palavramento.domain.board.Tile
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MutatorTest : FunSpec({
  test("NoMutator never overrides a tile's value") {
    Mutator.NoMutator.effectiveValueOf(Tile("L", 3)) shouldBe 3
  }

  test("ValuableLetter overrides a matching single-letter tile") {
    val mutator = Mutator.ValuableLetter('L', 10)
    mutator.effectiveValueOf(Tile("L", 3)) shouldBe 10
    mutator.effectiveValueOf(Tile("A", 1)) shouldBe 1
  }

  test("ValuableLetter does not override a digraph tile") {
    val mutator = Mutator.ValuableLetter('Q', 10)
    mutator.effectiveValueOf(Tile("QU", 4)) shouldBe 4
  }

  test("Digraphs and LetterInCorners never override a tile's value through effectiveValueOf") {
    Mutator.Digraphs(3).effectiveValueOf(Tile("L", 3)) shouldBe 3
    Mutator.LetterInCorners('O').effectiveValueOf(Tile("L", 3)) shouldBe 3
  }

  test("Rejects a letter outside A-Z") {
    shouldThrow<IllegalArgumentException> { Mutator.ValuableLetter('a', 5) }
    shouldThrow<IllegalArgumentException> { Mutator.LetterInCorners('1') }
  }

  test("Accepts the first and last letters of the alphabet, rejects just outside it") {
    Mutator.ValuableLetter('A', 5).letter shouldBe 'A'
    Mutator.ValuableLetter('Z', 5).letter shouldBe 'Z'
    Mutator.LetterInCorners('A').letter shouldBe 'A'
    Mutator.LetterInCorners('Z').letter shouldBe 'Z'
    shouldThrow<IllegalArgumentException> { Mutator.LetterInCorners('@') } // one before 'A'
    shouldThrow<IllegalArgumentException> { Mutator.LetterInCorners('[') } // one after 'Z'
  }

  test("Rejects a non-positive valuable value or digraph count, accepts exactly 1") {
    shouldThrow<IllegalArgumentException> { Mutator.ValuableLetter('A', 0) }
    shouldThrow<IllegalArgumentException> { Mutator.Digraphs(0) }
    Mutator.ValuableLetter('A', 1).value shouldBe 1
    Mutator.Digraphs(1).count shouldBe 1
  }

  test("Digraphs and LetterInCorners carry the fields they were built with") {
    Mutator.Digraphs(3).count shouldBe 3
    Mutator.LetterInCorners('O').letter shouldBe 'O'
  }
})
