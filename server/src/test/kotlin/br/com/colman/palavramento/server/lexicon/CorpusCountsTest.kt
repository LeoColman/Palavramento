// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CorpusCountsTest : FunSpec({
  test("Parses word and count pairs into a map") {
    val counts = CorpusCounts.parse(listOf("casa 120", "livro 30"))
    counts shouldBe mapOf("casa" to 120L, "livro" to 30L)
  }

  test("Blank lines are skipped") {
    val counts = CorpusCounts.parse(listOf("casa 120", "", "livro 30"))
    counts shouldBe mapOf("casa" to 120L, "livro" to 30L)
  }

  test("A line missing its count, or with a non-numeric count, is skipped") {
    val counts = CorpusCounts.parse(listOf("casa", "livro abc", "mesa 10"))
    counts shouldBe mapOf("mesa" to 10L)
  }

  test("If a word repeats, the first occurrence's count wins") {
    val counts = CorpusCounts.parse(listOf("casa 100", "casa 5"))
    counts shouldBe mapOf("casa" to 100L)
  }
})
