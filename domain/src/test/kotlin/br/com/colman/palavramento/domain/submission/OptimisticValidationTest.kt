// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.submission

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Path
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.lexicon.InMemoryLexicon
import br.com.colman.palavramento.domain.lexicon.Lexicon
import br.com.colman.palavramento.domain.lexicon.LexiconEntry
import br.com.colman.palavramento.domain.lexicon.toLexicon
import br.com.colman.palavramento.domain.protocol.ValidWord
import br.com.colman.palavramento.domain.solver.Solver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.stringPattern
import io.kotest.property.checkAll

// Adjacency-only helper (ADR 0014's equivalence argument): same shape as boardArb below (3x3), used
// only for Board.neighborsOf while building walkedPathArb; its own tile letters are never read.
private val topology = Board(3, List(9) { Tile("A", 1) })

private val letterArb = Arb.element(('A'..'E').toList())
private val plainTileArb: Arb<Tile> = letterArb.map { Tile(it.toString(), 1) }

/** Occasionally an alternatives tile (ADR 0015), so the equivalence argument also covers boards
 * where a path can spell more than one word. */
private val alternativesTileArb: Arb<Tile> =
  Arb.pair(letterArb, letterArb).filter { (first, second) -> first != second }
    .map { (first, second) -> Tile("$first/$second", 1) }
private val tileArb: Arb<Tile> = Arb.choice(plainTileArb, plainTileArb, plainTileArb, plainTileArb, alternativesTileArb)
private val boardArb: Arb<Board> = Arb.list(tileArb, 9..9).map { tiles -> Board(3, tiles) }
private val lexiconArb: Arb<Lexicon> = Arb.list(Arb.stringPattern("[A-E]{3,6}"), 0..40).map { words ->
  InMemoryLexicon(words.distinct().associateWith { LexiconEntry(it, 1) })
}
private val alreadyFoundArb: Arb<Set<String>> = Arb.list(Arb.stringPattern("[A-E]{3,6}"), 0..10).map { it.toSet() }

/** Any list of board indices, mostly not a valid path: exercises the InvalidPath/TooShort branches. */
private val arbitraryPathArb: Arb<List<Int>> = Arb.list(Arb.int(0..8), 0..7)

/**
 * A path built by random-walking board adjacency, never repeating a tile: exercises the
 * Accepted/NotAWord/AlreadyFound branches, where the solution-only lexicon and the full lexicon
 * could plausibly disagree if the equivalence did not hold.
 */
private val walkedPathArb: Arb<List<Int>> = Arb.list(Arb.int(0..1000), 1..6).map(::walkFromPicks)

private fun walkFromPicks(picks: List<Int>): List<Int> {
  val start = picks.first() % topology.tiles.size
  val path = mutableListOf(start)
  for (pick in picks.drop(1)) {
    val neighbors = topology.neighborsOf(path.last()).filter { it !in path }
    if (neighbors.isEmpty()) break
    path += neighbors[pick % neighbors.size]
  }
  return path
}

/** [board]'s own solution, reduced to the exact wire shape `RoundStart.validWords` carries. */
private fun solutionLexiconOf(board: Board, fullLexicon: Lexicon): Lexicon =
  Solver(fullLexicon).solve(board).map { ValidWord(it.normalized, it.display) }.toLexicon()

/**
 * The equivalence argument behind ADR 0014's optimistic client-side validation: a client that only
 * knows the round's own solution (what `RoundStart.validWords` sends) gets the exact same verdict
 * from [SubmissionValidator] as the server, which validates against the full lexicon. This is true
 * because [Solver] is exhaustive (SolverTest: "misses no word a naive full path enumeration finds"),
 * so the solution-only lexicon and the full lexicon agree on membership for every word reachable via
 * any valid path on the board - the only words [SubmissionValidator] ever needs to look up.
 */
class OptimisticValidationTest : FunSpec({
  test("Validating a random-walk path against the round's own solution matches the full lexicon") {
    checkAll(200, boardArb, lexiconArb, alreadyFoundArb, walkedPathArb) { board, lexicon, alreadyFound, path ->
      val solutionLexicon = solutionLexiconOf(board, lexicon)
      val viaSolution = SubmissionValidator.validate(board, solutionLexicon, alreadyFound, Path(path))
      val viaFullLexicon = SubmissionValidator.validate(board, lexicon, alreadyFound, Path(path))
      viaSolution shouldBe viaFullLexicon
    }
  }

  test("Validating an arbitrary (mostly invalid) path against the round's own solution matches the full lexicon") {
    checkAll(200, boardArb, lexiconArb, alreadyFoundArb, arbitraryPathArb) { board, lexicon, alreadyFound, path ->
      val solutionLexicon = solutionLexiconOf(board, lexicon)
      val viaSolution = SubmissionValidator.validate(board, solutionLexicon, alreadyFound, Path(path))
      val viaFullLexicon = SubmissionValidator.validate(board, lexicon, alreadyFound, Path(path))
      viaSolution shouldBe viaFullLexicon
    }
  }
})
