// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import kotlin.io.path.createTempDirectory

// See LexiconCompilerMainTest for why this goes through reflection: LexiconCompilerMain.kt declares
// its own top-level `main(Array<String>)` in this same package, so an unqualified call here would
// silently bind to whichever file's main happens to come first on the classpath.
private fun invokeLetterFrequencyReportMain(args: Array<String>) {
  val method = Class.forName(
    "br.com.colman.palavramento.server.lexicon.LetterFrequencyReportKt",
  ).getMethod("main", Array<String>::class.java)
  try {
    method.invoke(null, args)
  } catch (wrapped: InvocationTargetException) {
    throw wrapped.targetException
  }
}

/**
 * [LetterFrequencyReportKt.main] itself is thin wiring over [DicFileParser], [AffixFileParser],
 * [FrequencyList], [LexiconPipeline], [CanonicalForms] and [LetterFrequencyReport], each covered by
 * its own spec: this only checks the wiring holds together and the table actually gets printed.
 */
class LetterFrequencyReportMainTest : FunSpec({
  test("Rejects the wrong number of arguments") {
    shouldThrow<IllegalArgumentException> {
      invokeLetterFrequencyReportMain(arrayOf("only", "two"))
    }
  }

  test("Prints a markdown table computed from a tiny dictionary and frequency list") {
    val root = createTempDirectory("letter-frequency-report-main-test").toFile()
    val dicFile = File(root, "tiny.dic").apply { writeText("1\ncasa\n") }
    val affFile = File(root, "tiny.aff").apply { writeText("") }
    val frequencyFile = File(root, "tiny.freq").apply { writeText("casa 1\n") }

    val originalOut = System.out
    val capturedOut = ByteArrayOutputStream()
    System.setOut(PrintStream(capturedOut))
    try {
      invokeLetterFrequencyReportMain(arrayOf(dicFile.absolutePath, affFile.absolutePath, frequencyFile.absolutePath))
    } finally {
      System.setOut(originalOut)
    }

    val output = capturedOut.toString()
    output shouldContain "| Letra |"
    output shouldContain "| A | 2 |"
  }
})
