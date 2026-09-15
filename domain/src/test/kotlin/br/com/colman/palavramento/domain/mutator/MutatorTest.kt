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

  test("ForbiddenLetter blocks a word containing the letter, anywhere in it") {
    val mutator = Mutator.ForbiddenLetter('A')
    mutator.blocks("CASA") shouldBe true
    mutator.blocks("AMOR") shouldBe true
    mutator.blocks("LIMO") shouldBe false
  }

  test("Only ForbiddenLetter blocks words; other mutators never do") {
    Mutator.NoMutator.blocks("QUALQUER") shouldBe false
    Mutator.ValuableLetter('A', 5).blocks("QUALQUER") shouldBe false
    Mutator.MinimumLength(5).blocks("QUALQUER") shouldBe false
  }

  test("MinimumLength overrides the default of 3") {
    Mutator.MinimumLength(5).minimumLength() shouldBe 5
    Mutator.NoMutator.minimumLength() shouldBe DefaultMinimumLength
    Mutator.ValuableLetter('A', 5).minimumLength() shouldBe DefaultMinimumLength
    Mutator.ForbiddenLetter('A').minimumLength() shouldBe DefaultMinimumLength
  }

  test("Rejects a letter outside A-Z") {
    shouldThrow<IllegalArgumentException> { Mutator.ValuableLetter('a', 5) }
    shouldThrow<IllegalArgumentException> { Mutator.ForbiddenLetter('1') }
  }

  test("Accepts the first and last letters of the alphabet, rejects just outside it") {
    Mutator.ValuableLetter('A', 5).letter shouldBe 'A'
    Mutator.ValuableLetter('Z', 5).letter shouldBe 'Z'
    Mutator.ForbiddenLetter('A').letter shouldBe 'A'
    Mutator.ForbiddenLetter('Z').letter shouldBe 'Z'
    shouldThrow<IllegalArgumentException> { Mutator.ForbiddenLetter('@') } // one before 'A'
    shouldThrow<IllegalArgumentException> { Mutator.ForbiddenLetter('[') } // one after 'Z'
  }

  test("Rejects a non-positive valuable value or a length below 1, accepts exactly 1") {
    shouldThrow<IllegalArgumentException> { Mutator.ValuableLetter('A', 0) }
    shouldThrow<IllegalArgumentException> { Mutator.MinimumLength(0) }
    Mutator.ValuableLetter('A', 1).value shouldBe 1
    Mutator.MinimumLength(1).length shouldBe 1
  }
})
