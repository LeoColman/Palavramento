// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.scoring

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LetterValueTableTest : FunSpec({
  test("The default table has a version and a positive value for every letter") {
    val table = LetterValueTable.default
    table.version shouldBe 1
    ('A'..'Z').forEach { letter -> (table.value(letter) > 0) shouldBe true }
  }

  test("The default table matches the dossier's resolved ambiguities") {
    val table = LetterValueTable.default
    // O resolved to the smaller of its two listed values (dossier 1.3), grouped with A/E/S.
    table.value('O') shouldBe 1
    // T and C resolved to their row-3 value, grouped with M.
    table.value('T') shouldBe 3
    table.value('C') shouldBe 3
    // L kept its only listed value despite the asterisk.
    table.value('L') shouldBe 3
  }

  test("Parses a minimal custom table from JSON") {
    // A table built directly from a map still requires every letter, so this only exercises parsing
    // shape; fill every letter with the same value to keep the JSON short.
    val allLetters = ('A'..'Z').joinToString(separator = ",") { "\"$it\": 9" }
    val full = """{"version": 7, "values": {$allLetters}}"""
    val table = LetterValueTable.fromJson(full)
    table.version shouldBe 7
    table.value('A') shouldBe 9
    table.value('Z') shouldBe 9
  }

  test("Rejects a table missing a letter") {
    shouldThrow<IllegalArgumentException> {
      LetterValueTable(1, ('A'..'Y').associateWith { 1 })
    }
  }
})
