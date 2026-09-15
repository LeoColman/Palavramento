// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.generator

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class DigraphTableTest : FunSpec({
  test("The default table has a version and only samples the ADR 0012 candidates") {
    val table = DigraphTable.default
    table.version shouldBe 1
    val candidates = setOf("QU", "NH", "LH", "CH", "RR", "SS", "GU")
    val random = Random(42)
    repeat(200) { candidates shouldContain table.sample(random) }
  }

  test("AO is never a candidate: excluded so a digraph tile never mismatches words like caos") {
    val random = Random(1)
    val draws = List(500) { DigraphTable.default.sample(random) }
    draws.toSet() shouldNotContain "AO"
  }

  test("Sampling is deterministic for a given Random seed") {
    val table = DigraphTable.default
    val first = List(50) { table.sample(Random(123)) }
    val second = List(50) { table.sample(Random(123)) }
    first shouldBe second
  }

  test("A digraph with (almost) all the weight is drawn (almost) every time") {
    val table = DigraphTable(1, listOf(DigraphEntry("QU", 1000.0), DigraphEntry("CH", 0.001)))
    val draws = List(500) { table.sample(Random(7)) }
    (draws.count { it == "QU" } > 480) shouldBe true
  }

  test("Rejects an empty table") {
    shouldThrow<IllegalArgumentException> { DigraphTable(1, emptyList()) }
  }

  test("Rejects a non-positive weight") {
    shouldThrow<IllegalArgumentException> { DigraphTable(1, listOf(DigraphEntry("QU", 0.0))) }
  }

  test("Rejects an entry that is not exactly two normalized A-Z letters") {
    shouldThrow<IllegalArgumentException> { DigraphTable(1, listOf(DigraphEntry("Q", 1.0))) }
    shouldThrow<IllegalArgumentException> { DigraphTable(1, listOf(DigraphEntry("QUE", 1.0))) }
    shouldThrow<IllegalArgumentException> { DigraphTable(1, listOf(DigraphEntry("A1", 1.0))) }
  }

  test("Rejects a letter just outside A-Z, accepts the boundary letters A and Z") {
    shouldThrow<IllegalArgumentException> { DigraphTable(1, listOf(DigraphEntry("Q@", 1.0))) } // one before 'A'
    shouldThrow<IllegalArgumentException> { DigraphTable(1, listOf(DigraphEntry("Q[", 1.0))) } // one after 'Z'
    DigraphTable(1, listOf(DigraphEntry("AZ", 1.0))).sample(Random(1)) shouldBe "AZ"
  }

  test("sample() picks the bucket strictly above the drawn point, not on it (boundary semantics)") {
    // Two equal-weight entries: cumulative [1.0, 2.0], total 2.0. A draw of exactly 0.5 targets 1.0,
    // which must land in the SECOND bucket (indexOfFirst uses '>', not '>='): a '>=' mutant, or a
    // multiplication-to-division mutant on `random.nextDouble() * total`, would both instead pick "QU".
    val table = DigraphTable(1, listOf(DigraphEntry("QU", 1.0), DigraphEntry("CH", 1.0)))
    val halfway = object : Random() {
      override fun nextBits(bitCount: Int): Int = error("not used by this fixture")
      override fun nextDouble(): Double = 0.5
    }
    table.sample(halfway) shouldBe "CH"
  }
})
