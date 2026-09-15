// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.solver

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Path
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.board.isValidOn
import br.com.colman.palavramento.domain.board.spell
import br.com.colman.palavramento.domain.lexicon.InMemoryLexicon
import br.com.colman.palavramento.domain.lexicon.Lexicon
import br.com.colman.palavramento.domain.lexicon.LexiconEntry
import br.com.colman.palavramento.domain.lexicon.lookup
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.stringPattern
import io.kotest.property.checkAll
import kotlin.random.Random
import kotlin.system.measureTimeMillis

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

private val letterArb = Arb.element(('A'..'E').toList())
private val boardArb: Arb<Board> = Arb.list(letterArb, 9..9).map { letters ->
  Board(3, letters.map { Tile(it.toString(), 1) })
}
private val lexiconArb: Arb<Lexicon> = Arb.list(Arb.stringPattern("[A-E]{3,6}"), 0..40).map { words ->
  InMemoryLexicon(words.distinct().associateWith { LexiconEntry(it, 1) })
}

/** Reference implementation (dossier 10): every simple path on the board, checked against the lexicon directly. */
private fun naiveSolve(board: Board, lexicon: Lexicon, minLength: Int = 3): List<SolvedWord> {
  val best = HashMap<String, SolvedWord>()
  val visited = BooleanArray(board.tiles.size)
  val path = mutableListOf<Int>()

  fun visit(score: Int) {
    if (path.size >= minLength) {
      val word = path.joinToString(separator = "") { board.tiles[it].letters }
      val entry = lexicon.lookup(word)
      if (entry != null) {
        val existing = best[word]
        if (existing == null || score > existing.score) {
          best[word] = SolvedWord(word, entry.display, path.toList(), score, WordTier.Common)
        }
      }
    }
    for (neighbor in board.neighborsOf(path.last())) {
      if (!visited[neighbor]) {
        visited[neighbor] = true
        path += neighbor
        visit(score + board.tiles[neighbor].value)
        path.removeAt(path.lastIndex)
        visited[neighbor] = false
      }
    }
  }

  for (start in board.tiles.indices) {
    visited[start] = true
    path += start
    visit(board.tiles[start].value)
    path.removeAt(path.lastIndex)
    visited[start] = false
  }
  return best.values.toList()
}

class SolverTest : FunSpec({
  test("Finds LIMO on the reference board with the expected score") {
    val lexicon = InMemoryLexicon.of("limo")
    val words = Solver(lexicon).solve(referenceBoard())
    words.map { it.normalized } shouldContain "LIMO"
    words.first { it.normalized == "LIMO" }.score shouldBe 17
  }

  test("Accented lexicon words are found via their unaccented board path (dossier 1.6/10)") {
    // "ação" normalizes to ACAO: A -> C -> A -> O over a fully-connected 2x2 grid, two distinct A tiles.
    val acaoBoard = Board(2, listOf(Tile("A", 1), Tile("C", 3), Tile("A", 1), Tile("O", 1)))
    Solver(InMemoryLexicon.of("ação")).solve(acaoBoard).map { it.normalized } shouldContain "ACAO"

    // "pêssego" normalizes to PESSEGO: a snake path over a 3x3 board, two distinct E tiles and two S tiles.
    val pessegoBoard = Board(
      3,
      listOf(
        Tile("P", 1), Tile("E", 1), Tile("S", 1),
        Tile("G", 1), Tile("E", 1), Tile("S", 1),
        Tile("O", 1), Tile("X", 1), Tile("X", 1),
      ),
    )
    val words = Solver(InMemoryLexicon.of("pêssego")).solve(pessegoBoard)
    words.map { it.normalized } shouldContain "PESSEGO"
    words.first { it.normalized == "PESSEGO" }.display shouldBe "pêssego"
  }

  test("A digraph tile is walked as one unit through the lexicon") {
    val board = Board(2, listOf(Tile("QU", 10), Tile("A", 1), Tile("L", 3), Tile("E", 1)))
    val lexicon = InMemoryLexicon.of("quale")
    val words = Solver(lexicon).solve(board)
    words.map { it.normalized } shouldContain "QUALE"
  }

  test("Finds a word starting with QUE through a QU digraph tile (ADR 0012)") {
    // Q U / E .. a QU tile adjacent to E, I: spells QUE.
    val board = Board(2, listOf(Tile("QU", 10), Tile("E", 1), Tile("I", 2), Tile("S", 1)))
    val lexicon = InMemoryLexicon.of("que")
    val words = Solver(lexicon).solve(board)
    words.map { it.normalized } shouldContain "QUE"
    // Score is the QU tile's own value (10) plus E's (1): the digraph is not looked up letter by
    // letter, its whole tile value counts once.
    words.first { it.normalized == "QUE" }.score shouldBe 11
  }

  test("Keeps the best-scoring path when several paths spell the same word") {
    // A 2x2 diamond where two distinct B tiles, each a different value, both sit between the same
    // pair of A tiles: two distinct paths spell "ABA", one through each B.
    val board = Board(2, listOf(Tile("A", 1), Tile("B", 1), Tile("B", 9), Tile("A", 1)))
    val lexicon = InMemoryLexicon.of("aba")
    val words = Solver(lexicon).solve(board)
    val aba = words.first { it.normalized == "ABA" }
    // Best path uses the high-value B (index 2, adjacent to both A's) rather than the low-value one.
    aba.score shouldBe 11
  }

  test("A word is only returned once even though several paths spell it") {
    val board = Board(2, listOf(Tile("A", 1), Tile("B", 1), Tile("B", 9), Tile("A", 1)))
    val lexicon = InMemoryLexicon.of("aba")
    Solver(lexicon).solve(board).count { it.normalized == "ABA" } shouldBe 1
  }

  test("A word shorter than 3 letters is never returned, even if the lexicon has it") {
    val board = Board(1, listOf(Tile("A", 1)))
    val lexicon = InMemoryLexicon(mapOf("A" to LexiconEntry("a", 1)))
    Solver(lexicon).solve(board) shouldBe emptyList()
  }

  test("Common and expert tiers follow the cutoff parameter") {
    val board = Board(2, listOf(Tile("C", 3), Tile("A", 1), Tile("T", 3), Tile("S", 1)))
    val lexicon = InMemoryLexicon(
      mapOf("CAT" to LexiconEntry("cat", 1), "CATS" to LexiconEntry("cats", 100)),
    )
    val words = Solver(lexicon).solve(board, commonCutoff = 50)
    words.first { it.normalized == "CAT" }.tier shouldBe WordTier.Common
    words.first { it.normalized == "CATS" }.tier shouldBe WordTier.Expert
  }

  test("Every word the solver returns has a valid path on the board that spells it") {
    checkAll(30, boardArb, lexiconArb) { board, lexicon ->
      Solver(lexicon).solve(board).forEach { word ->
        val path = Path(word.path)
        path.isValidOn(board) shouldBe true
        path.spell(board) shouldBe word.normalized
      }
    }
  }

  test("The solver misses no word a naive full path enumeration finds") {
    checkAll(30, boardArb, lexiconArb) { board, lexicon ->
      val fast = Solver(lexicon).solve(board).map { it.normalized }.toSet()
      val naive = naiveSolve(board, lexicon).map { it.normalized }.toSet()
      fast shouldBe naive
    }
  }

  test("Solves a 4x4 board against a large synthetic lexicon well under a second") {
    val random = Random(1)
    val words = (0 until 20_000).map { randomWord(random) }
    val lexicon = InMemoryLexicon(words.distinct().associateWith { LexiconEntry(it, 1) })
    val board = Board(4, List(16) { Tile(('A' + random.nextInt(26)).toString(), 1) })

    val elapsedMs = measureTimeMillis { Solver(lexicon).solve(board) }
    (elapsedMs < 1000) shouldBe true
  }
})

private fun randomWord(random: Random): String {
  val length = 3 + random.nextInt(6)
  return (1..length).joinToString(separator = "") { ('A' + random.nextInt(26)).toString() }
}
