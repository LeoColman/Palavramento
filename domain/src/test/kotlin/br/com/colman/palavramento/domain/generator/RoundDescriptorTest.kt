// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.generator

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

  test("commonMin is always positive") {
    checkAll(Arb.long()) { seed ->
      (RoundDescriptorPicker.pick(seed).commonMin > 0) shouldBe true
    }
  }
})
