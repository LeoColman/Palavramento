// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import br.com.colman.palavramento.domain.lexicon.TrieLexicon
import java.io.File
import java.io.FileOutputStream

private const val ExpectedArgCount = 5

/**
 * Entry point for the `compileLexicon` Gradle task (`server/build.gradle.kts`): turns the vendored
 * Hunspell dictionary and the frequency list into `forms.tsv` and the binary trie artifact consumed
 * at runtime by `LexiconLoader` (dossier §11 phase 1).
 *
 * Args: `<dic> <aff> <frequencyList> <binOut> <tsvOut>`.
 */
fun main(args: Array<String>) {
  // The Gradle task always passes exactly five absolute paths; a mismatch means a build script bug.
  require(args.size == ExpectedArgCount) { "Usage: LexiconCompilerMain <dic> <aff> <frequencyList> <binOut> <tsvOut>" }
  val (dicPath, affPath, frequencyPath, binOutPath, tsvOutPath) = args

  val startedAt = System.nanoTime()
  val dictionary = DicFileParser.parse(File(dicPath).readLexiconLines())
  val affixes = AffixFileParser.parse(File(affPath).readLexiconLines())
  val frequency = FrequencyList.parse(File(frequencyPath).readLexiconLines())

  val rows = LexiconPipeline.buildRows(dictionary, affixes, frequency)
  val tsvOut = File(tsvOutPath)
  FormsTsv.write(rows, tsvOut)

  val entries = CanonicalForms.collapse(rows)
  val lexicon = TrieLexicon.build(entries)
  val binOut = File(binOutPath)
  binOut.parentFile?.mkdirs()
  FileOutputStream(binOut).use { lexicon.write(it) }

  val elapsedMs = (System.nanoTime() - startedAt) / NanosPerMilli
  println(
    "Lexicon compiled: ${dictionary.size} dic stems -> ${rows.size} surviving forms -> " +
      "${entries.size} normalized entries -> ${lexicon.nodeCount} trie nodes, " +
      "${binOut.length()} artifact bytes, ${elapsedMs}ms",
  )
}

private const val NanosPerMilli = 1_000_000L
