// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.generator

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class LetterWeightTableTest : FunSpec({
  test("The default table has a version and only samples A-Z") {
    val table = LetterWeightTable.default
    table.version shouldBe 1
    val random = Random(42)
    repeat(200) { (table.sample(random) in 'A'..'Z') shouldBe true }
  }

  test("Sampling is deterministic for a given Random seed") {
    val table = LetterWeightTable.default
    val first = List(50) { table.sample(Random(123)) }
    val second = List(50) { table.sample(Random(123)) }
    first shouldBe second
  }

  test("A letter with (almost) all the weight is drawn (almost) every time") {
    val weights = ('A'..'Z').associateWith { 0.001 } + mapOf('A' to 1000.0)
    val table = LetterWeightTable(1, weights)
    val draws = List(500) { table.sample(Random(7)) }
    (draws.count { it == 'A' } > 480) shouldBe true
  }

  test("Rejects a table missing a letter") {
    shouldThrow<IllegalArgumentException> { LetterWeightTable(1, ('A'..'Y').associateWith { 1.0 }) }
  }

  test("Rejects a non-positive weight") {
    shouldThrow<IllegalArgumentException> { LetterWeightTable(1, ('A'..'Z').associateWith { 1.0 } + mapOf('A' to 0.0)) }
  }
})
