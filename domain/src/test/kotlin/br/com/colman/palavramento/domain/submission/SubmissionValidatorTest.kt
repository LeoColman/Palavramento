// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.submission

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Path
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.lexicon.InMemoryLexicon
import br.com.colman.palavramento.domain.mutator.Mutator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

// C A T S
private fun board() = Board(2, listOf(Tile("C", 3), Tile("A", 1), Tile("T", 3), Tile("S", 1)))

class SubmissionValidatorTest : FunSpec({
  test("Accepts a valid word not yet found, scoring the sum of its tile values") {
    val result = SubmissionValidator.validate(
      board(),
      Mutator.NoMutator,
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
      Mutator.NoMutator,
      InMemoryLexicon.of("cat"),
      alreadyFound = emptySet(),
      path = Path(listOf(0, 1, 0)),
    )
    result shouldBe SubmissionResult.Rejected(RejectionReason.InvalidPath)
  }

  test("Rejects a word shorter than the minimum length before checking anything else") {
    val result = SubmissionValidator.validate(
      board(),
      Mutator.MinimumLength(4),
      InMemoryLexicon.of("cat"),
      alreadyFound = emptySet(),
      path = Path(listOf(0, 1, 2)),
    )
    result shouldBe SubmissionResult.Rejected(RejectionReason.TooShort)
  }

  test("Rejects a word blocked by a forbidden-letter mutator before checking the lexicon") {
    val result = SubmissionValidator.validate(
      board(),
      Mutator.ForbiddenLetter('A'),
      InMemoryLexicon.of("cat"),
      alreadyFound = emptySet(),
      path = Path(listOf(0, 1, 2)),
    )
    result shouldBe SubmissionResult.Rejected(RejectionReason.BlockedByMutator)
  }

  test("Rejects a path that does not spell a lexicon word") {
    val result = SubmissionValidator.validate(
      board(),
      Mutator.NoMutator,
      InMemoryLexicon.of("cats"),
      alreadyFound = emptySet(),
      path = Path(listOf(0, 1, 2)),
    )
    result shouldBe SubmissionResult.Rejected(RejectionReason.NotAWord)
  }

  test("Rejects a word already found, by normalized form regardless of path") {
    val result = SubmissionValidator.validate(
      board(),
      Mutator.NoMutator,
      InMemoryLexicon.of("cat"),
      alreadyFound = setOf("CAT"),
      path = Path(listOf(0, 1, 2)),
    )
    result shouldBe SubmissionResult.Rejected(RejectionReason.AlreadyFound)
  }

  test("A valuable-letter mutator changes the score") {
    val result = SubmissionValidator.validate(
      board(),
      Mutator.ValuableLetter('C', 10),
      InMemoryLexicon.of("cat"),
      alreadyFound = emptySet(),
      path = Path(listOf(0, 1, 2)),
    )
    result shouldBe SubmissionResult.Accepted("cat", 14)
  }
})
