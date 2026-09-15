// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import br.com.colman.palavramento.domain.lexicon.LexiconEntry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CanonicalFormsTest : FunSpec({
  test("A single surviving form becomes its own entry") {
    val entries = CanonicalForms.collapse(listOf(FormRow("casa", "CASA", 95)))
    entries shouldBe mapOf("CASA" to LexiconEntry("casa", 95))
  }

  test("Two forms collapsing to the same normalized spelling keep the better-ranked display") {
    val entries = CanonicalForms.collapse(
      listOf(FormRow("pais", "PAIS", 467), FormRow("país", "PAIS", 595)),
    )
    entries shouldBe mapOf("PAIS" to LexiconEntry("pais", 467))
  }

  test("Order does not matter: the better rank wins regardless of which row comes first") {
    val entries = CanonicalForms.collapse(
      listOf(FormRow("país", "PAIS", 595), FormRow("pais", "PAIS", 467)),
    )
    entries.getValue("PAIS").display shouldBe "pais"
    entries.getValue("PAIS").frequencyRank shouldBe 467
  }

  test("An unranked form uses LexiconEntry.Unranked") {
    val entries = CanonicalForms.collapse(listOf(FormRow("xilofone", "XILOFONE", null)))
    entries.getValue("XILOFONE").frequencyRank shouldBe LexiconEntry.Unranked
  }

  test("A ranked form beats an unranked one collapsing to the same entry") {
    val entries = CanonicalForms.collapse(
      listOf(FormRow("zzz", "ZZZ", null), FormRow("zzy", "ZZZ", 10)),
    )
    entries.getValue("ZZZ") shouldBe LexiconEntry("zzy", 10)
  }

  test("Tie-break when both rows are unranked: the lexicographically smaller display wins") {
    val entries = CanonicalForms.collapse(
      listOf(FormRow("zebra", "ZEBRA", null), FormRow("aardvark", "ZEBRA", null)),
    )
    entries.getValue("ZEBRA").display shouldBe "aardvark"
  }

  test("Tie-break is deterministic regardless of row order") {
    val forward = CanonicalForms.collapse(
      listOf(FormRow("bbb", "X", null), FormRow("aaa", "X", null)),
    )
    val backward = CanonicalForms.collapse(
      listOf(FormRow("aaa", "X", null), FormRow("bbb", "X", null)),
    )
    forward shouldBe backward
    forward.getValue("X").display shouldBe "aaa"
  }
})
