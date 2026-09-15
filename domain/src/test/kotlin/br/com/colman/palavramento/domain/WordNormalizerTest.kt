// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

class WordNormalizerTest : FunSpec({
  context("Accented forms match unaccented tiles") {
    withData(
      "loâ" to "LOA",
      "moiâ" to "MOIA",
      "ação" to "ACAO",
      "pêssego" to "PESSEGO",
      "ç" to "C",
      "pinguïm" to "PINGUIM",
    ) { (form, normalized) ->
      WordNormalizer.normalize(form) shouldBe normalized
    }
  }

  test("Every Portuguese letter normalizes into A-Z") {
    val portugueseLetters = "abcdefghijklmnopqrstuvwxyzáàâãéêíóôõúüçÁÀÂÃÉÊÍÓÔÕÚÜÇ".toList()
    val words = Arb.list(Arb.element(portugueseLetters), 1..20).map { it.joinToString("") }

    checkAll(words) { word ->
      WordNormalizer.normalize(word) shouldMatch Regex("[A-Z]+")
    }
  }

  test("Normalizing keeps the letter count") {
    checkAll(Arb.list(Arb.element("aãeéçoõu".toList()), 1..20).map { it.joinToString("") }) { word ->
      WordNormalizer.normalize(word).length shouldBe word.length
    }
  }
})
