// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.scoring

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LetterValueTableTest : FunSpec({
  test("The default table has a version and a positive value for every letter") {
    val table = LetterValueTable.default
    table.version shouldBe 2
    ('A'..'Z').forEach { letter -> (table.value(letter) > 0) shouldBe true }
  }

  test("The default table is the one calibrated in docs/calibracao-letras.md") {
    val table = LetterValueTable.default
    "AES".forEach { table.value(it) shouldBe 1 }
    "INOR".forEach { table.value(it) shouldBe 2 }
    "CDLMTU".forEach { table.value(it) shouldBe 3 }
    "HP".forEach { table.value(it) shouldBe 4 }
    "BG".forEach { table.value(it) shouldBe 5 }
    "FVZ".forEach { table.value(it) shouldBe 6 }
    "JQX".forEach { table.value(it) shouldBe 8 }
    "KWY".forEach { table.value(it) shouldBe 10 }
  }

  test("Lookups ignore letter case") {
    LetterValueTable.default.value('q') shouldBe 8
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
