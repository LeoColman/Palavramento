// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class LexiconPipelineTest : FunSpec({
  test("A form rejected by the canonical filter, such as a hyphenated compound, never becomes a row") {
    val dictionary = DicFileParser.parse(listOf("1", "guarda-chuva"))
    val affixes = AffixFileParser.parse(emptyList())

    val rows = LexiconPipeline.buildRows(dictionary, affixes, FrequencyList.parse(emptyList()))

    rows shouldHaveSize 0
  }

  test("A form whose normalized spelling fails the length bound never becomes a row") {
    val dictionary = DicFileParser.parse(listOf("1", "ab"))
    val affixes = AffixFileParser.parse(emptyList())

    val rows = LexiconPipeline.buildRows(dictionary, affixes, FrequencyList.parse(emptyList()))

    rows shouldHaveSize 0
  }

  test("An acceptable form survives with its normalized spelling and frequency rank") {
    val dictionary = DicFileParser.parse(listOf("1", "casa"))
    val affixes = AffixFileParser.parse(emptyList())
    val frequency = FrequencyList.parse(listOf("casa 5"))

    val rows = LexiconPipeline.buildRows(dictionary, affixes, frequency)

    rows shouldContainExactlyInAnyOrder listOf(FormRow("casa", "CASA", 1))
  }

  test("The same canonical form reachable through two independent affix rules is kept only once") {
    val dictionary = DicFileParser.parse(listOf("1", "menino/AB"))
    val affixes = AffixFileParser.parse(
      listOf(
        "SFX A Y 1",
        "SFX A   0     s        [^s]",
        "SFX B Y 1",
        "SFX B   0     s        .",
      ),
    )

    val rows = LexiconPipeline.buildRows(dictionary, affixes, FrequencyList.parse(emptyList()))

    rows.count { it.canonical == "meninos" } shouldBe 1
  }
})
