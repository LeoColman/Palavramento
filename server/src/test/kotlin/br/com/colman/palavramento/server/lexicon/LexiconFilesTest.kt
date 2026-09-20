// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import java.io.File
import java.util.UUID

private fun tempLexiconFile(content: String): File {
  val file = File.createTempFile("lexicon-files-test-${UUID.randomUUID()}", ".txt")
  file.deleteOnExit()
  file.writeText(content, Charsets.UTF_8)
  return file
}

class LexiconFilesTest : FunSpec({
  test("A leading byte order mark is dropped from the first line") {
    val file = tempLexiconFile("﻿casa\nlivro")
    file.readLexiconLines() shouldContainExactly listOf("casa", "livro")
  }

  test("A file without a byte order mark is read unchanged") {
    val file = tempLexiconFile("casa\nlivro")
    file.readLexiconLines() shouldContainExactly listOf("casa", "livro")
  }
})
