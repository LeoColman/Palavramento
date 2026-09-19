// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.generator

import br.com.colman.palavramento.domain.mutator.Mutator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

class RoundDescriptorTest : FunSpec({
  test("The same seed always picks the same descriptor") {
    checkAll(Arb.long()) { seed ->
      RoundDescriptorPicker.pick(seed) shouldBe RoundDescriptorPicker.pick(seed)
    }
  }

  test("Different seeds can pick different mutators, over enough draws") {
    val mutators = (0 until 50L).map { RoundDescriptorPicker.pick(it).mutator::class }.toSet()
    (mutators.size > 1) shouldBe true
  }

  test("Over enough draws, all five mutator outcomes appear") {
    val mutators = (0 until 200L).map { RoundDescriptorPicker.pick(it).mutator::class }.toSet()
    mutators shouldBe setOf(
      Mutator.NoMutator::class,
      Mutator.ValuableLetter::class,
      Mutator.Digraphs::class,
      Mutator.LetterInCorners::class,
      Mutator.OneOrOther::class,
    )
  }

  test("Digraphs count is always between 2 and 4") {
    (0 until 200L).forEach { seed ->
      val mutator = RoundDescriptorPicker.pick(seed).mutator
      if (mutator is Mutator.Digraphs) (mutator.count in 2..4) shouldBe true
    }
  }

  test("LetterInCorners letter is always from the corner pool") {
    val cornerLetters = "AEIOSR".toSet()
    (0 until 200L).forEach { seed ->
      val mutator = RoundDescriptorPicker.pick(seed).mutator
      if (mutator is Mutator.LetterInCorners) (mutator.letter in cornerLetters) shouldBe true
    }
  }

  test("OneOrOther's first letter is always one of the four vowels A, E, I, O") {
    val vowels = "AEIO".toSet()
    (0 until 200L).forEach { seed ->
      val mutator = RoundDescriptorPicker.pick(seed).mutator
      if (mutator is Mutator.OneOrOther) (mutator.first in vowels) shouldBe true
    }
  }

  test("OneOrOther's second letter is always one of the documented consonant pool") {
    val consonants = "RSNTMCLDPF".toSet()
    (0 until 200L).forEach { seed ->
      val mutator = RoundDescriptorPicker.pick(seed).mutator
      if (mutator is Mutator.OneOrOther) (mutator.second in consonants) shouldBe true
    }
  }

  test("Over enough draws, every letter of the OneOrOther consonant pool appears") {
    val consonants = "RSNTMCLDPF".toSet()
    val seen = (0 until 2_000L).mapNotNull { seed ->
      (RoundDescriptorPicker.pick(seed).mutator as? Mutator.OneOrOther)?.second
    }.toSet()
    seen shouldBe consonants
  }

  test("commonMin is always positive") {
    checkAll(Arb.long()) { seed ->
      (RoundDescriptorPicker.pick(seed).commonMin > 0) shouldBe true
    }
  }
})
