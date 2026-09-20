// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import br.com.colman.palavramento.domain.lexicon.LexiconEntry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File
import java.util.UUID
import kotlin.io.path.createTempDirectory

class FormsTsvTest : FunSpec({
  test("Writes canonical, normalized and rank columns tab-separated, one row per line") {
    val file = File.createTempFile("forms-tsv-test", ".tsv")
    file.deleteOnExit()

    FormsTsv.write(listOf(FormRow("casa", "CASA", 95), FormRow("livro", "LIVRO", 200)), file)

    file.readLines() shouldContainExactly listOf("casa\tCASA\t95", "livro\tLIVRO\t200")
  }

  test("An unranked row writes the Unranked sentinel instead of a rank number") {
    val file = File.createTempFile("forms-tsv-test", ".tsv")
    file.deleteOnExit()

    FormsTsv.write(listOf(FormRow("xilofone", "XILOFONE", null)), file)

    file.readLines().single() shouldBe "xilofone\tXILOFONE\t${LexiconEntry.Unranked}"
  }

  test("Creates missing parent directories before writing") {
    val root = createTempDirectory("forms-tsv-test").toFile()
    val nested = File(root, "nested/dir/forms.tsv")

    FormsTsv.write(listOf(FormRow("casa", "CASA", 1)), nested)

    nested.exists() shouldBe true
    nested.readLines() shouldContainExactly listOf("casa\tCASA\t1")
  }

  test("A file with no parent directory component is written directly, without needing mkdirs") {
    // A bare (no slash) relative File has a null parentFile: this exercises that branch of the
    // null-safe mkdirs() call. Unique per run so parallel PIT minions sharing this working directory
    // never collide on the same path.
    val file = File("forms-tsv-bare-name-${UUID.randomUUID()}.tsv")
    file.parentFile shouldBe null
    try {
      FormsTsv.write(listOf(FormRow("casa", "CASA", 1)), file)
      file.readLines() shouldContainExactly listOf("casa\tCASA\t1")
    } finally {
      file.delete()
    }
  }
})
