// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Path
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.board.isValidOn
import br.com.colman.palavramento.domain.board.spell
import br.com.colman.palavramento.domain.lexicon.InMemoryLexicon
import br.com.colman.palavramento.domain.lexicon.LexiconEntry
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.stats.AcceptedWord
import br.com.colman.palavramento.domain.stats.RoundStatsCalculator
import br.com.colman.palavramento.domain.submission.SubmissionResult
import br.com.colman.palavramento.domain.submission.SubmissionValidator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

// Dossier 1.2 / 10 reference round, straight from the reference screenshots:
// L O A R
// M I C T
// P V R I
// E O S M
private fun referenceBoard() = Board(
  4,
  listOf(
    Tile("L", 10), Tile("O", 2), Tile("A", 1), Tile("R", 2),
    Tile("M", 3), Tile("I", 2), Tile("C", 4), Tile("T", 4),
    Tile("P", 5), Tile("V", 6), Tile("R", 2), Tile("I", 2),
    Tile("E", 1), Tile("O", 2), Tile("S", 1), Tile("M", 3),
  ),
)

private fun referenceLexicon() = InMemoryLexicon(
  mapOf(
    "LIMO" to LexiconEntry("limo", 1),
    "LOA" to LexiconEntry("loâ", 2),
    "VIA" to LexiconEntry("via", 3),
    "PILO" to LexiconEntry("pilo", 4),
    "MOIA" to LexiconEntry("moiâ", 5),
    "MOI" to LexiconEntry("moi", 6),
  ),
)

private data class Find(val path: List<Int>, val expectedScore: Int, val acceptedAtMs: Long)

// limo=10+2+3+2=17, loâ=10+2+1=13, via=6+2+1=9, pilo=5+2+10+2=19, moiâ=3+2+2+1=8, moi=3+2+2=7.
private val referenceFinds = listOf(
  Find(listOf(0, 5, 4, 1), 17, 1_000), // L I M O
  Find(listOf(0, 1, 2), 13, 2_000), // L O A
  Find(listOf(9, 5, 2), 9, 3_000), // V I A
  Find(listOf(8, 5, 0, 1), 19, 4_000), // P I L O
  Find(listOf(4, 1, 5, 2), 8, 5_000), // M O I A
  Find(listOf(4, 1, 5), 7, 6_000), // M O I
)

class ReferenceRoundTest : FunSpec({
  test("Every reference word has a valid, adjacent, non-reusing path spelling it") {
    val board = referenceBoard()
    referenceFinds.forEach { find -> Path(find.path).isValidOn(board) shouldBe true }
  }

  test("Each reference word scores exactly what the dossier's arithmetic shows") {
    val board = referenceBoard()
    val lexicon = referenceLexicon()
    var alreadyFound = emptySet<String>()

    referenceFinds.forEach { find ->
      val result = SubmissionValidator.validate(board, Mutator.NoMutator, lexicon, alreadyFound, Path(find.path))
      result.shouldBeInstanceOf<SubmissionResult.Accepted>()
      result.score shouldBe find.expectedScore
      alreadyFound = alreadyFound + Path(find.path).spell(board)
    }
  }

  test("6 words, 73 points, average points 12.2, average length 3.5 (dossier 1.2/10)") {
    val board = referenceBoard()
    val accepted = referenceFinds.map { find ->
      AcceptedWord(find.expectedScore, Path(find.path).spell(board).length, find.acceptedAtMs)
    }

    accepted.size shouldBe 6
    accepted.sumOf { it.score } shouldBe 73

    val stats = RoundStatsCalculator.compute(startedAtEpochMs = 0, acceptedWords = accepted)
    stats.words shouldBe 6
    stats.points shouldBe 73
    stats.averagePoints shouldBe 12.2
    stats.averageLength shouldBe 3.5
  }
})
