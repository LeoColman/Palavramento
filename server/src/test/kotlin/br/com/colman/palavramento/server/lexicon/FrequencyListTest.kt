// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FrequencyListTest : FunSpec({
  test("Rank is the 1-based line number, most frequent first") {
    val list = FrequencyList.parse(listOf("que 100", "não 90", "casa 5"))
    list.rankOf("que") shouldBe 1
    list.rankOf("não") shouldBe 2
    list.rankOf("casa") shouldBe 3
  }

  test("A word absent from the list has no rank") {
    val list = FrequencyList.parse(listOf("que 100"))
    list.rankOf("xyzw") shouldBe null
  }

  test("Lookup keeps diacritics: pais and país are different keys") {
    val list = FrequencyList.parse(listOf("pais 100", "país 50"))
    list.rankOf("pais") shouldBe 1
    list.rankOf("país") shouldBe 2
  }

  test("If a word repeats, the first (most frequent) occurrence wins the rank") {
    val list = FrequencyList.parse(listOf("casa 100", "livro 50", "casa 10"))
    list.rankOf("casa") shouldBe 1
  }

  test("Blank lines do not shift the rank of later words") {
    val list = FrequencyList.parse(listOf("que 100", "", "não 90"))
    list.rankOf("não") shouldBe 3
  }
})
