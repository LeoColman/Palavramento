// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class DicFileParserTest : FunSpec({
  test("The first line, the entry count, is skipped") {
    val entries = DicFileParser.parse(listOf("2", "casa/D", "livro"))
    entries.map { it.stem } shouldContainExactly listOf("casa", "livro")
  }

  test("A stem with no slash has no flags") {
    val entries = DicFileParser.parse(listOf("1", "livro"))
    entries.single() shouldBe DicEntry("livro", emptySet())
  }

  test("A stem with flags after the slash keeps every flag character") {
    val entries = DicFileParser.parse(listOf("1", "casar/ajkLMYÀÂ"))
    entries.single() shouldBe DicEntry("casar", setOf('a', 'j', 'k', 'L', 'M', 'Y', 'À', 'Â'))
  }

  test("Blank lines are skipped") {
    val entries = DicFileParser.parse(listOf("2", "", "casa", ""))
    entries.map { it.stem } shouldContainExactly listOf("casa")
  }
})
