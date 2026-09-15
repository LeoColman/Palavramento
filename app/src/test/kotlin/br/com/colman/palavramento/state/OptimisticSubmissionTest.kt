// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.state

import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.protocol.FoundWord
import br.com.colman.palavramento.domain.protocol.ValidWord
import br.com.colman.palavramento.domain.submission.RejectionReason
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

// C A / T S: a 2x2 board, fully connected by adjacency (every pair of indices is adjacent).
private fun catBoard() = listOf(Tile("C", 3), Tile("A", 1), Tile("T", 3), Tile("S", 1))

private fun catRound(
  validWords: List<ValidWord> = listOf(ValidWord("CAT", "cat")),
  foundWords: List<FoundWord> = emptyList(),
  endsAt: Long = 120_000,
) = MatchUiState.InRound(
  roundId = "round-1",
  board = catBoard(),
  mutator = Mutator.NoMutator,
  themeTitle = "Grade padrao",
  themeSubtitle = "15 palavras comuns",
  maxScore = 7,
  maxWords = 1,
  startsAt = 0,
  endsAt = endsAt,
  foundWords = foundWords,
  runningScore = foundWords.sumOf { it.score },
  runningWords = foundWords.size,
  validWords = validWords,
)

class OptimisticSubmissionTest : FunSpec({

  test("A valid word is accepted: found word, score and running totals applied, path marked pending") {
    val decision = OptimisticSubmission.decide(catRound(), path = listOf(0, 1, 2), nowMs = 1_000)

    val accept = decision.shouldBeInstanceOf<OptimisticSubmission.Decision.Accept>()
    accept.newState.foundWords shouldBe listOf(FoundWord("cat", 7, listOf(0, 1, 2)))
    accept.newState.runningScore shouldBe 7
    accept.newState.runningWords shouldBe 1
    accept.newState.lastFeedback shouldBe SubmissionFeedback.Accepted("cat", 7, listOf(0, 1, 2))
    accept.newState.pendingPaths shouldBe setOf(listOf(0, 1, 2))
  }

  test("A word not in the round's solution is rejected as NotAWord and nothing is applied but feedback") {
    // C A S is not "cat".
    val decision = OptimisticSubmission.decide(catRound(), path = listOf(0, 1, 3), nowMs = 1_000)

    val reject = decision.shouldBeInstanceOf<OptimisticSubmission.Decision.Reject>()
    reject.newState.foundWords shouldBe emptyList()
    reject.newState.runningScore shouldBe 0
    reject.newState.lastFeedback shouldBe SubmissionFeedback.Rejected(RejectionReason.NotAWord, listOf(0, 1, 3))
    reject.newState.pendingPaths shouldBe emptySet()
  }

  test("A path that reuses the same tile is rejected as InvalidPath") {
    val decision = OptimisticSubmission.decide(catRound(), path = listOf(0, 0), nowMs = 1_000)

    decision.shouldBeInstanceOf<OptimisticSubmission.Decision.Reject>().newState.lastFeedback shouldBe
      SubmissionFeedback.Rejected(RejectionReason.InvalidPath, listOf(0, 0))
  }

  test("A word already found is rejected as AlreadyFound, by normalized form regardless of path") {
    val alreadyFound = listOf(FoundWord("cat", 7, listOf(0, 1, 2)))
    val decision =
      OptimisticSubmission.decide(catRound(foundWords = alreadyFound), path = listOf(0, 1, 2), nowMs = 1_000)

    decision.shouldBeInstanceOf<OptimisticSubmission.Decision.Reject>().newState.lastFeedback shouldBe
      SubmissionFeedback.Rejected(RejectionReason.AlreadyFound, listOf(0, 1, 2))
  }

  test("Empty validWords defers to the server: no local verdict, exactly like before this feature") {
    val decision =
      OptimisticSubmission.decide(catRound(validWords = emptyList()), path = listOf(0, 1, 2), nowMs = 1_000)

    decision shouldBe OptimisticSubmission.Decision.Defer
  }

  test("A round already over by the synced server clock defers to the server") {
    val decision = OptimisticSubmission.decide(catRound(endsAt = 500), path = listOf(0, 1, 2), nowMs = 501)

    decision shouldBe OptimisticSubmission.Decision.Defer
  }

  test("Exactly at endsAt is still within the round: a local verdict is produced") {
    val decision = OptimisticSubmission.decide(catRound(endsAt = 500), path = listOf(0, 1, 2), nowMs = 500)

    decision.shouldBeInstanceOf<OptimisticSubmission.Decision.Accept>()
  }

  test("A null clock (no sample yet) does not block a local verdict") {
    val decision = OptimisticSubmission.decide(catRound(), path = listOf(0, 1, 2), nowMs = null)

    decision.shouldBeInstanceOf<OptimisticSubmission.Decision.Accept>()
  }
})
