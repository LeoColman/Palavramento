// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AffixConditionTest : FunSpec({
  test("A dot matches any single character, anchored at the end") {
    val condition = AffixCondition.parse(".")
    condition.matchesEnd("casar") shouldBe true
    condition.matchesEnd("") shouldBe false
  }

  test("A literal string must match exactly, at the end for suffixes") {
    val condition = AffixCondition.parse("ar")
    condition.matchesEnd("casar") shouldBe true
    condition.matchesEnd("cantar") shouldBe true
    condition.matchesEnd("comer") shouldBe false
    condition.matchesEnd("a") shouldBe false
  }

  test("A literal string anchors at the start for prefixes") {
    val condition = AffixCondition.parse("des")
    condition.matchesStart("desfazer") shouldBe true
    condition.matchesStart("fazer") shouldBe false
  }

  test("A positive character class accepts only its members") {
    val condition = AffixCondition.parse("[aei]r")
    condition.matchesEnd("falar") shouldBe true
    condition.matchesEnd("comer") shouldBe true
    condition.matchesEnd("partir") shouldBe true
    condition.matchesEnd("por") shouldBe false
  }

  test("A negated character class rejects its members and accepts everything else") {
    val condition = AffixCondition.parse("[^cgç]ar")
    condition.matchesEnd("falar") shouldBe true
    condition.matchesEnd("buscar") shouldBe false
    condition.matchesEnd("chegar") shouldBe false
    condition.matchesEnd("realçar") shouldBe false
  }

  test("A condition longer than the word never matches") {
    val condition = AffixCondition.parse("[aei]r")
    condition.matchesEnd("r") shouldBe false
    condition.matchesStart("r") shouldBe false
  }

  test("matchesEnd boundary: word length exactly equal to the condition length still matches, one shorter does not") {
    val condition = AffixCondition.parse("ar")
    condition.matchesEnd("ar") shouldBe true
    condition.matchesEnd("r") shouldBe false
  }

  test("matchesStart boundary: word length exactly equal to the condition length still matches, one shorter does not") {
    val condition = AffixCondition.parse("ar")
    condition.matchesStart("ar") shouldBe true
    condition.matchesStart("a") shouldBe false
  }

  test("matchesStart with a character class rejects a mismatch anywhere in the sequence, not just at the start") {
    val condition = AffixCondition.parse("[aei]r")
    condition.matchesStart("arco") shouldBe true
    condition.matchesStart("orco") shouldBe false
  }

  test("matchesStart with a negated character class rejects an excluded first unit or a mismatched later one") {
    val condition = AffixCondition.parse("[^cg]ar")
    condition.matchesStart("bar!") shouldBe true
    condition.matchesStart("car!") shouldBe false
    condition.matchesStart("box!") shouldBe false
  }

  test("An empty character class is rejected, a single-character one is the smallest valid class") {
    shouldThrow<IllegalArgumentException> { AffixCondition.parse("[]") }
    shouldThrow<IllegalArgumentException> { AffixCondition.parse("[^]") }
    AffixCondition.parse("[a]").matchesEnd("a") shouldBe true
  }

  test("Length reports the number of condition units, brackets counting as one") {
    AffixCondition.parse("[^ã]o").length shouldBe 2
    AffixCondition.parse("ar").length shouldBe 2
    AffixCondition.parse(".").length shouldBe 1
  }

  test("The empty condition matches at any position, including the empty word") {
    AffixCondition.Always.matchesEnd("") shouldBe true
    AffixCondition.Always.matchesStart("qualquer") shouldBe true
  }
})
