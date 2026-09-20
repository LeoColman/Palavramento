// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import br.com.colman.palavramento.domain.lexicon.LexiconEntry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class LetterFrequencyReportTest : FunSpec({
  test("compute counts each letter occurrence, boundary letters A and Z included, repeats add up") {
    val entries = mapOf(
      "AZ" to LexiconEntry("az", 1),
      "AA" to LexiconEntry("aa", 2),
    )

    val report = LetterFrequencyReport.compute(entries, emptyMap())

    report.plainCounts['A' - 'A'] shouldBe 3L // "AZ" has one A, "AA" has two
    report.plainCounts['Z' - 'A'] shouldBe 1L
  }

  test("weighted counts add the corpus weight per letter occurrence, only for entries with a corpus hit") {
    val entries = mapOf(
      "CASA" to LexiconEntry("casa", 5),
      "XILOFONE" to LexiconEntry("xilofone", LexiconEntry.Unranked),
    )
    val corpusCounts = mapOf("casa" to 100L)

    val report = LetterFrequencyReport.compute(entries, corpusCounts)

    report.weightedCounts['A' - 'A'] shouldBe 200L // "casa" has two A's, weight 100 each
    report.weightedCounts['X' - 'A'] shouldBe 0L // "xilofone" has no corpus count: contributes nothing
  }

  test("toMarkdownTable renders every letter, all-zero counts as exactly 0.000%") {
    val report = LetterFrequencyReport.compute(emptyMap(), emptyMap())

    val table = report.toMarkdownTable()

    table shouldContain "| A | 0 | 0.000% | 0 | 0.000% |"
    table shouldContain "| Z | 0 | 0.000% | 0 | 0.000% |"
  }

  test("toMarkdownTable computes percentages correctly and sorts letters by plain count descending") {
    val entries = mapOf("AAB" to LexiconEntry("aab", 1))
    val report = LetterFrequencyReport.compute(entries, emptyMap())

    val lines = report.toMarkdownTable().lines()
    val aLine = lines.first { it.startsWith("| A ") }
    val bLine = lines.first { it.startsWith("| B ") }

    aLine shouldContain "| 2 | 66.667% |"
    bLine shouldContain "| 1 | 33.333% |"
    lines.indexOf(aLine) shouldBe lines.indexOf(bLine) - 1
  }
})
