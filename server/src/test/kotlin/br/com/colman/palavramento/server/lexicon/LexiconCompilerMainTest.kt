// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import br.com.colman.palavramento.domain.lexicon.TrieLexicon
import br.com.colman.palavramento.domain.lexicon.lookup
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import kotlin.io.path.createTempDirectory

// LexiconCompilerMain.kt and LetterFrequencyReport.kt both declare a top-level `main(Array<String>)`
// in this same package. Kotlin has no way to call one of them qualified by file (the "FileKt" facade
// name is a Java interop convention, not something Kotlin's own resolver accepts), and an unqualified
// call would silently bind to whichever is first on the classpath. Reflection targets this file's
// main unambiguously, and unwraps the real exception `require()` throws from InvocationTargetException.
private fun invokeLexiconCompilerMain(args: Array<String>) {
  val method = Class.forName(
    "br.com.colman.palavramento.server.lexicon.LexiconCompilerMainKt",
  ).getMethod("main", Array<String>::class.java)
  try {
    method.invoke(null, args)
  } catch (wrapped: InvocationTargetException) {
    throw wrapped.targetException
  }
}

/**
 * Exercises [LexiconCompilerMainKt.main] end to end against a tiny fixture dictionary: this is the
 * Gradle `compileLexicon` task's own entry point, and both files it writes are real build outputs,
 * not throwaway wiring, so they are worth checking directly rather than only through their pieces.
 */
class LexiconCompilerMainTest : FunSpec({
  test("Rejects the wrong number of arguments before touching any file") {
    shouldThrow<IllegalArgumentException> {
      invokeLexiconCompilerMain(arrayOf("only", "four", "args", "here"))
    }
  }

  test("Compiles a tiny dictionary end to end: forms.tsv and the binary artifact both land on disk") {
    val root = createTempDirectory("lexicon-compiler-main-test").toFile()
    val dicFile = File(root, "in/tiny.dic").apply {
      parentFile.mkdirs()
      writeText("1\ncasa\n")
    }
    val affFile = File(root, "in/tiny.aff").apply { writeText("") }
    val frequencyFile = File(root, "in/tiny.freq").apply { writeText("casa 1\n") }
    // Deliberately nested and not pre-created, so main() must mkdirs() its own output directory.
    val binOut = File(root, "out/nested/lexicon.bin")
    val tsvOut = File(root, "out/nested/forms.tsv")

    val originalOut = System.out
    val capturedOut = ByteArrayOutputStream()
    System.setOut(PrintStream(capturedOut))
    try {
      invokeLexiconCompilerMain(
        arrayOf(
          dicFile.absolutePath,
          affFile.absolutePath,
          frequencyFile.absolutePath,
          binOut.absolutePath,
          tsvOut.absolutePath,
        ),
      )
    } finally {
      System.setOut(originalOut)
    }

    tsvOut.readLines() shouldContainExactly listOf("casa\tCASA\t1")

    binOut.exists() shouldBe true
    val lexicon = FileInputStream(binOut).use { TrieLexicon.read(it) }
    lexicon.lookup("CASA").shouldNotBeNull().display shouldBe "casa"

    capturedOut.toString() shouldContain "Lexicon compiled: 1 dic stems -> 1 surviving forms -> 1 normalized entries"
  }
})
