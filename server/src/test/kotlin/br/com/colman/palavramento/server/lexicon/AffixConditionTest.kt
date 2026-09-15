// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

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
