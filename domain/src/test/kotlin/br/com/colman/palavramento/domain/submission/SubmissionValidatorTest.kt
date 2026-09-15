// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.submission

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Path
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.lexicon.InMemoryLexicon
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

// C A T S
private fun board() = Board(2, listOf(Tile("C", 3), Tile("A", 1), Tile("T", 3), Tile("S", 1)))

class SubmissionValidatorTest : FunSpec({
  test("Accepts a valid word not yet found, scoring the sum of its tile values") {
    val result = SubmissionValidator.validate(
      board(),
      InMemoryLexicon.of("cat"),
      alreadyFound = emptySet(),
      path = Path(listOf(0, 1, 2)),
    )
    result shouldBe SubmissionResult.Accepted("cat", 7)
    (result as SubmissionResult.Accepted).word shouldBe "cat"
  }

  test("Rejects a path that is not valid on the board") {
    val result = SubmissionValidator.validate(
      board(),
      InMemoryLexicon.of("cat"),
      alreadyFound = emptySet(),
      path = Path(listOf(0, 1, 0)),
    )
    result shouldBe SubmissionResult.Rejected(RejectionReason.InvalidPath)
  }

  test("Rejects a word shorter than the dossier's minimum of 3 letters, before checking the lexicon") {
    val result = SubmissionValidator.validate(
      board(),
      InMemoryLexicon.of("ca"),
      alreadyFound = emptySet(),
      path = Path(listOf(0, 1)),
    )
    result shouldBe SubmissionResult.Rejected(RejectionReason.TooShort)
  }

  test("Rejects a path that does not spell a lexicon word") {
    val result = SubmissionValidator.validate(
      board(),
      InMemoryLexicon.of("cats"),
      alreadyFound = emptySet(),
      path = Path(listOf(0, 1, 2)),
    )
    result shouldBe SubmissionResult.Rejected(RejectionReason.NotAWord)
  }

  test("Rejects a word already found, by normalized form regardless of path") {
    val result = SubmissionValidator.validate(
      board(),
      InMemoryLexicon.of("cat"),
      alreadyFound = setOf("CAT"),
      path = Path(listOf(0, 1, 2)),
    )
    result shouldBe SubmissionResult.Rejected(RejectionReason.AlreadyFound)
  }

  test("Only the traced copy of a valuable letter scores its inflated value") {
    // C(10) A C(3) T: the same word through either C scores that C's own value.
    val board = Board(2, listOf(Tile("C", 10), Tile("A", 1), Tile("C", 3), Tile("T", 3)))
    val throughValuable = SubmissionValidator.validate(
      board,
      InMemoryLexicon.of("cat"),
      alreadyFound = emptySet(),
      path = Path(listOf(0, 1, 3)),
    )
    val throughPlain = SubmissionValidator.validate(
      board,
      InMemoryLexicon.of("cat"),
      alreadyFound = emptySet(),
      path = Path(listOf(2, 1, 3)),
    )
    throughValuable shouldBe SubmissionResult.Accepted("cat", 14)
    throughPlain shouldBe SubmissionResult.Accepted("cat", 7)
  }
})
