// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class AffixFileParserTest : FunSpec({
  test("Parses a simple suffix class with a plain strip and add") {
    val aff = AffixFileParser.parse(
      listOf(
        "SFX A Y 1",
        "SFX A   0     s        .",
      ),
    )
    val rule = aff.classes.getValue('A').rules.single()
    rule.strip shouldBe ""
    rule.add shouldBe "s"
    rule.condition.matchesEnd("gato") shouldBe true
  }

  test("A '0' strip or add means empty, not the literal character") {
    val aff = AffixFileParser.parse(listOf("SFX A Y 1", "SFX A   ar     0        ar"))
    val rule = aff.classes.getValue('A').rules.single()
    rule.strip shouldBe "ar"
    rule.add shouldBe ""
  }

  test("Parses a prefix class as a prefix, cross-product Y as true") {
    val aff = AffixFileParser.parse(listOf("PFX B Y 1", "PFX B   0     re      ."))
    val cls = aff.classes.getValue('B')
    cls.kind shouldBe AffixKind.Prefix
    cls.crossProduct shouldBe true
  }

  test("Cross-product N is parsed as false") {
    val aff = AffixFileParser.parse(listOf("SFX C N 1", "SFX C   0     s        ."))
    aff.classes.getValue('C').crossProduct shouldBe false
  }

  test("Reads continuation flags after a slash on the add field") {
    val aff = AffixFileParser.parse(listOf("SFX D Y 1", "SFX D   0     Alagoas/ÝB    AL"))
    val rule = aff.classes.getValue('D').rules.single()
    rule.add shouldBe "Alagoas"
    rule.continuationFlags shouldContainExactly setOf('Ý', 'B')
  }

  test("A rule with no slash has no continuation flags") {
    val aff = AffixFileParser.parse(listOf("SFX D Y 1", "SFX D   0     s        ."))
    aff.classes.getValue('D').rules.single().continuationFlags shouldBe emptySet()
  }

  test("Reads several rules for the same class") {
    val aff = AffixFileParser.parse(
      listOf(
        "SFX E Y 2",
        "SFX E   0     s        [^s]",
        "SFX E   0     es       s",
      ),
    )
    aff.classes.getValue('E').rules shouldHaveSize 2
  }

  test("Parses FORBIDDENWORD and NOSUGGEST flags") {
    val aff = AffixFileParser.parse(listOf("NOSUGGEST Ý", "FORBIDDENWORD ý"))
    aff.noSuggestFlag shouldBe 'Ý'
    aff.forbiddenWordFlag shouldBe 'ý'
  }

  test("Missing FORBIDDENWORD or NOSUGGEST leaves the flag null") {
    val aff = AffixFileParser.parse(emptyList())
    aff.noSuggestFlag shouldBe null
    aff.forbiddenWordFlag shouldBe null
  }

  test("Comments, blank lines and unrelated directives are ignored") {
    val aff = AffixFileParser.parse(
      listOf(
        "# a comment",
        "",
        "SET UTF-8",
        "TRY esianrt",
        "MAP 1",
        "MAP aáà",
        "SFX A Y 1",
        "SFX A   0     s        .",
      ),
    )
    aff.classes.keys shouldContainExactly setOf('A')
  }
})
