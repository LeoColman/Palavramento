// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.generator

import br.com.colman.palavramento.domain.lexicon.InMemoryLexicon
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.scoring.LetterValueTable
import br.com.colman.palavramento.domain.solver.Solver
import br.com.colman.palavramento.domain.solver.WordTier
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** Draws almost only from [letters], so a small lexicon built from the same alphabet is reachable. */
private fun weightsFavoring(letters: String): LetterWeightTable {
  val heavy = letters.toSet()
  return LetterWeightTable(1, ('A'..'Z').associateWith { if (it in heavy) 100.0 else 0.0001 })
}

private fun uniformValues(): LetterValueTable = LetterValueTable(1, ('A'..'Z').associateWith { 1 })

private fun smallLexicon() = InMemoryLexicon.of(
  "cara", "casa", "carro", "cor", "ora", "rosa", "sabor", "carta", "arco", "rasa", "cesta", "asa",
)

// Tiny, easily reachable criteria (dossier 3 defaults need a real lexicon, per dossier 10).
private fun reachableCriteria() = GenerationCriteria(
  commonMin = 1,
  maxScoreRange = 0..1_000_000,
  totalWordsMin = 1,
  maxAttempts = 500,
)

private fun generator() = BoardGenerator(Solver(smallLexicon()), uniformValues(), weightsFavoring("CASOR"))

class BoardGeneratorTest : FunSpec({
  test("The same seed always produces the same board") {
    val result1 = generator().generate(
      seed = 42,
      size = 3,
      commonCutoff = Int.MAX_VALUE,
      criteria = reachableCriteria(),
    )
    val result2 = generator().generate(
      seed = 42,
      size = 3,
      commonCutoff = Int.MAX_VALUE,
      criteria = reachableCriteria(),
    )
    result1.board shouldBe result2.board
    result1.attempts shouldBe result2.attempts
    result1.seed shouldBe 42
    (result1.attempts >= 1) shouldBe true
  }

  test("Gives up with a clear error once relaxation cannot possibly reach the criteria in time") {
    // totalWordsMin starts absurdly high (1 billion) and relaxation only shrinks it 10% per block:
    // with maxAttempts = 1, the hard cap of 50 relaxation rounds is reached (0.9^50 ~ 0.005, so the
    // minimum is still around 5 million) long before it could ever fall to something reachable.
    val criteria = GenerationCriteria(commonMin = 0, totalWordsMin = 1_000_000_000, maxAttempts = 1)
    val exception = shouldThrow<IllegalStateException> {
      generator().generate(seed = 1, size = 3, commonCutoff = Int.MAX_VALUE, criteria = criteria)
    }
    exception.message shouldContain "50 attempts"
  }

  test("A different seed can produce a different board") {
    val boards = (1L..10L).map {
      generator().generate(seed = it, size = 3, commonCutoff = Int.MAX_VALUE, criteria = reachableCriteria()).board
    }
    (boards.toSet().size > 1) shouldBe true
  }

  test("Every accepted board satisfies the criteria that were actually used") {
    (1L..15L).forEach { seed ->
      val result = generator().generate(
        seed = seed,
        size = 3,
        commonCutoff = Int.MAX_VALUE,
        criteria = reachableCriteria()
      )
      val commonWords = result.solution.count { it.tier == WordTier.Common }
      val totalWords = result.solution.size
      val maxScore = result.solution.sumOf { it.score }

      (commonWords >= result.criteriaUsed.commonMin) shouldBe true
      (totalWords >= result.criteriaUsed.totalWordsMin) shouldBe true
      (maxScore in result.criteriaUsed.maxScoreRange) shouldBe true
    }
  }

  test("A valuable letter mutator is baked into the tile values players see") {
    val result = generator().generate(
      seed = 7,
      size = 4,
      mutator = Mutator.ValuableLetter('C', 10),
      commonCutoff = Int.MAX_VALUE,
      criteria = reachableCriteria(),
    )
    result.board.tiles.forEach { tile -> tile.value shouldBe if (tile.letters == "C") 10 else 1 }
    result.board.tiles.any { it.letters == "C" } shouldBe true
  }

  test("Criteria relax when the original ones are not met within maxAttempts") {
    // An empty lexicon never satisfies commonMin >= 1, so the very first block must relax.
    val impossible = BoardGenerator(Solver(InMemoryLexicon(emptyMap())), uniformValues(), weightsFavoring("CASOR"))
    val criteria = GenerationCriteria(commonMin = 1, totalWordsMin = 0, maxScoreRange = 0..1_000_000, maxAttempts = 1)
    val result = impossible.generate(seed = 1, size = 3, commonCutoff = Int.MAX_VALUE, criteria = criteria)
    (result.criteriaUsed.commonMin < criteria.commonMin) shouldBe true
  }
})
