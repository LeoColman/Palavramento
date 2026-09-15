// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import br.com.colman.palavramento.domain.lexicon.LexiconEntry
import java.io.File
import java.util.Locale

private const val AlphabetSize = 26
private const val ExpectedArgCount = 3
private const val PercentScale = 100.0

/**
 * Letter frequency of the compiled lexicon, for the tile value calibration the dossier asks for in
 * phase 1 (§1.3, §11): the real value table is authored by hand in `docs/calibracao-letras.md`, but
 * the frequencies backing it come from here so they can be recomputed after the dictionary changes.
 *
 * Two counts are kept per letter (dossier §11 phase 1):
 * - [plainCounts]: one vote per occurrence per distinct normalized entry, so a rare inflected form
 *   counts the same as `casa`. This is what a "how many playable words contain this letter" question
 *   asks.
 * - [weightedCounts]: the same, but every ranked entry's occurrences are multiplied by its corpus
 *   count from `pt_br_50k.txt` (not its rank), so common words dominate as they would in actual play.
 *   Unranked ("especialista") entries do not contribute here: they have no corpus count to weight by.
 */
class LetterFrequencyReport private constructor(val plainCounts: LongArray, val weightedCounts: LongArray) {
  fun toMarkdownTable(): String {
    val totalPlain = plainCounts.sum().toDouble()
    val totalWeighted = weightedCounts.sum().toDouble()
    val header = "| Letra | Ocorrencias (formas) | % formas | Ocorrencias (ponderado por frequencia) | % ponderado |"
    val divider = "|---|---|---|---|---|"
    val rows = (0 until AlphabetSize).sortedByDescending { plainCounts[it] }.joinToString("\n") { index ->
      val letter = 'A' + index
      val plain = plainCounts[index]
      val weighted = weightedCounts[index]
      val plainPercent = if (totalPlain == 0.0) 0.0 else plain * PercentScale / totalPlain
      val weightedPercent = if (totalWeighted == 0.0) 0.0 else weighted * PercentScale / totalWeighted
      val plainPercentText = String.format(Locale.ROOT, "%.3f", plainPercent)
      val weightedPercentText = String.format(Locale.ROOT, "%.3f", weightedPercent)
      "| $letter | $plain | $plainPercentText% | $weighted | $weightedPercentText% |"
    }
    return listOf(header, divider, rows).joinToString("\n")
  }

  companion object {
    fun compute(entries: Map<String, LexiconEntry>, corpusCounts: Map<String, Long>): LetterFrequencyReport {
      val plain = LongArray(AlphabetSize)
      val weighted = LongArray(AlphabetSize)
      for ((normalized, entry) in entries) {
        for (letter in normalized) plain[letter - 'A']++
        val weight = corpusCounts[entry.display.lowercase()] ?: continue
        for (letter in normalized) weighted[letter - 'A'] += weight
      }
      return LetterFrequencyReport(plain, weighted)
    }
  }
}

/** Parses `word count` lines into raw corpus counts, the weights [LetterFrequencyReport] needs. */
object CorpusCounts {
  fun parse(lines: List<String>): Map<String, Long> {
    val counts = LinkedHashMap<String, Long>()
    for (line in lines) {
      if (line.isBlank()) continue
      val word = line.substringBefore(' ')
      val count = line.substringAfter(' ', missingDelimiterValue = "").toLongOrNull() ?: continue
      counts.putIfAbsent(word, count)
    }
    return counts
  }
}

/**
 * Re-runnable letter frequency report (dossier §11 phase 1, `docs/calibracao-letras.md`).
 * Args: `<dic> <aff> <frequencyList>`. Prints a pt-BR markdown table to stdout; paste it into the doc.
 */
fun main(args: Array<String>) {
  require(args.size == ExpectedArgCount) { "Usage: LetterFrequencyReportKt <dic> <aff> <frequencyList>" }
  val (dicPath, affPath, frequencyPath) = args

  val dictionary = DicFileParser.parse(File(dicPath).readLexiconLines())
  val affixes = AffixFileParser.parse(File(affPath).readLexiconLines())
  val frequencyLines = File(frequencyPath).readLexiconLines()
  val frequency = FrequencyList.parse(frequencyLines)
  val corpusCounts = CorpusCounts.parse(frequencyLines)

  val rows = LexiconPipeline.buildRows(dictionary, affixes, frequency)
  val entries = CanonicalForms.collapse(rows)
  val report = LetterFrequencyReport.compute(entries, corpusCounts)
  println(report.toMarkdownTable())
}
