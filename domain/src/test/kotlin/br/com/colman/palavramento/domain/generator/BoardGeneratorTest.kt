// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.generator

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.lexicon.InMemoryLexicon
import br.com.colman.palavramento.domain.lexicon.LexiconEntry
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.scoring.LetterValueTable
import br.com.colman.palavramento.domain.solver.SolvedWord
import br.com.colman.palavramento.domain.solver.Solver
import br.com.colman.palavramento.domain.solver.WordTier
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldHaveSize
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

/**
 * Every 3-letter `XAY`/`XSY` pair over the heavy alphabet "CASOR" (both spelled out): wherever the
 * `A/S` alternatives tile lands, any two of its (up to 8) neighbors drawn from that same alphabet
 * complete both a valid "A" word and a valid "S" word through it, so [reachableCriteria]'s
 * `minWordsPerOption` converges in very few attempts instead of hoping for a specific rare shape.
 */
private fun oneOrOtherLexicon(): InMemoryLexicon {
  val heavy = "CASOR".toList()
  val entries = buildMap {
    for (x in heavy) {
      for (y in heavy) {
        put("${x}A$y", LexiconEntry("${x}a$y", 1))
        put("${x}S$y", LexiconEntry("${x}s$y", 1))
      }
    }
  }
  return InMemoryLexicon(entries)
}

// Tiny, easily reachable criteria (dossier 3 defaults need a real lexicon, per dossier 10). specialTiles
// only requires 1 word per tile/corner/option, not the production defaults of 5/2/2, for the same reason.
private fun reachableCriteria() = GenerationCriteria(
  commonMin = 1,
  maxScoreRange = 0..1_000_000,
  totalWordsMin = 1,
  maxAttempts = 500,
  specialTiles = SpecialTileCriteria(minWordsPerTile = 1, minWordsPerCorner = 1, minWordsPerOption = 1),
)

private fun generator() = BoardGenerator(Solver(smallLexicon()), uniformValues(), weightsFavoring("CASOR"))

private fun oneOrOtherGenerator() =
  BoardGenerator(Solver(oneOrOtherLexicon()), uniformValues(), weightsFavoring("CASOR"))

/**
 * Every `XddY` combination of a [DigraphTable.default] digraph `dd` between two letters of the heavy
 * alphabet "CASOR": wherever a digraph tile lands and whichever of the seven digraphs it draws, some
 * word uses it, the same reasoning as [oneOrOtherLexicon] but for the fixed, small digraph pool.
 */
private fun digraphLexicon(): InMemoryLexicon {
  val digraphs = listOf("QU", "NH", "LH", "CH", "RR", "SS", "GU")
  val heavy = "CASOR".toList()
  val entries = buildMap {
    for (digraph in digraphs) {
      for (x in heavy) {
        for (y in heavy) {
          put("$x$digraph$y", LexiconEntry("$x$digraph$y", 1))
        }
      }
    }
  }
  return InMemoryLexicon(entries)
}

private fun digraphGenerator() = BoardGenerator(Solver(digraphLexicon()), uniformValues(), weightsFavoring("CASOR"))

/** True when [a] and [b] are within 8-direction reach of each other on a [size]x[size] board (test-local,
 * independent re-derivation of BoardGenerator's own private `areAdjacent`). */
private fun adjacent(size: Int, a: Int, b: Int): Boolean {
  if (a == b) return false
  val rowDelta = a / size - b / size
  val colDelta = a % size - b % size
  return rowDelta in -1..1 && colDelta in -1..1
}

private fun wordsUsingTile(position: Int, solution: List<SolvedWord>): List<SolvedWord> =
  solution.filter { position in it.path }

/** Which option of the tile at [position] a solved [word] used (test-local re-derivation, see
 * BoardGenerator's own private `optionUsedAt`: every tile contributes a fixed-length spelling
 * regardless of option, so the character offset is computable from the board alone). */
private fun optionUsedAt(board: Board, word: SolvedWord, position: Int): Char {
  val indexInPath = word.path.indexOf(position)
  val offset = word.path.take(indexInPath).sumOf { board.tiles[it].options.first().length }
  return word.normalized[offset]
}

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

  test("A valuable letter inflates exactly one copy of its letter, for any seed") {
    (1L..15L).forEach { seed ->
      val result = generator().generate(
        seed = seed,
        size = 4,
        mutator = Mutator.ValuableLetter('C', 10),
        commonCutoff = Int.MAX_VALUE,
        criteria = reachableCriteria(),
      )
      val inflated = result.board.tiles.filter { it.value == 10 }
      inflated shouldHaveSize 1
      inflated.single().letters shouldBe "C"
      // uniformValues(): every other tile, other copies of C included, keeps its base value.
      result.board.tiles.filter { it.value != 10 }.forEach { it.value shouldBe 1 }
    }
  }

  test("A valuable letter the draw did not produce still gets its one inflated tile") {
    // weightsFavoring("CASOR") practically never draws a Z, so the generator has to place it.
    val result = generator().generate(
      seed = 11,
      size = 4,
      mutator = Mutator.ValuableLetter('Z', 10),
      commonCutoff = Int.MAX_VALUE,
      criteria = reachableCriteria(),
    )
    result.board.tiles.filter { it.letters == "Z" } shouldHaveSize 1
    result.board.tiles.single { it.letters == "Z" }.value shouldBe 10
  }

  test("cornersOf computes the exact four corner indices for a size, not just their count") {
    cornersOf(4) shouldBe listOf(0, 3, 12, 15)
    cornersOf(2) shouldBe listOf(0, 1, 2, 3) // every tile of a 2x2 board is a corner
    cornersOf(1) shouldBe listOf(0, 0, 0, 0) // degenerate: the single tile is every corner at once
  }

  test("centersOf computes the exact non-edge indices for a size, not just their count") {
    centersOf(4) shouldBe listOf(5, 6, 9, 10)
    centersOf(3) shouldBe listOf(4) // the single center cell of a 3x3 board
    centersOf(2) shouldBe emptyList() // every tile of a 2x2 board touches an edge
    centersOf(1) shouldBe emptyList()
  }

  test("LetterInCorners puts the letter in all four corners, for any seed") {
    (1L..15L).forEach { seed ->
      val result = generator().generate(
        seed = seed,
        size = 4,
        mutator = Mutator.LetterInCorners('O'),
        commonCutoff = Int.MAX_VALUE,
        criteria = reachableCriteria(),
      )
      cornersOf(4).forEach { corner -> result.board.tiles[corner].letters shouldBe "O" }
    }
  }

  test("LetterInCorners gives the corner tiles their normal (non-inflated) value") {
    val result = generator().generate(
      seed = 3,
      size = 4,
      mutator = Mutator.LetterInCorners('O'),
      commonCutoff = Int.MAX_VALUE,
      criteria = reachableCriteria(),
    )
    cornersOf(4).forEach { corner -> result.board.tiles[corner].value shouldBe 1 } // uniformValues() = 1
  }

  test("Digraphs(n) yields exactly n two-letter tiles, all from the digraph table, for any seed") {
    val digraphLetters = setOf("QU", "NH", "LH", "CH", "RR", "SS", "GU")
    (1L..15L).forEach { seed ->
      val result = generator().generate(
        seed = seed,
        size = 4,
        mutator = Mutator.Digraphs(3),
        commonCutoff = Int.MAX_VALUE,
        criteria = reachableCriteria(),
      )
      val digraphTiles = result.board.tiles.filter { it.letters.length == 2 }
      digraphTiles shouldHaveSize 3
      digraphTiles.forEach { it.letters shouldBeIn digraphLetters }
    }
  }

  test("A digraph tile's value is the sum of its letters' base values") {
    val result = generator().generate(
      seed = 5,
      size = 4,
      mutator = Mutator.Digraphs(4),
      commonCutoff = Int.MAX_VALUE,
      criteria = reachableCriteria(),
    )
    result.board.tiles.filter { it.letters.length == 2 }.forEach { tile ->
      tile.value shouldBe tile.letters.sumOf { uniformValues().value(it) }
    }
  }

  test("Digraphs positions are deterministic by seed, same as the rest of the board") {
    val result1 = generator().generate(
      seed = 9,
      size = 4,
      mutator = Mutator.Digraphs(3),
      commonCutoff = Int.MAX_VALUE,
      criteria = reachableCriteria(),
    )
    val result2 = generator().generate(
      seed = 9,
      size = 4,
      mutator = Mutator.Digraphs(3),
      commonCutoff = Int.MAX_VALUE,
      criteria = reachableCriteria(),
    )
    result1.board shouldBe result2.board
  }

  test("Criteria relax when the original ones are not met within maxAttempts") {
    // An empty lexicon never satisfies commonMin >= 1, so the very first block must relax.
    val impossible = BoardGenerator(Solver(InMemoryLexicon(emptyMap())), uniformValues(), weightsFavoring("CASOR"))
    val criteria = GenerationCriteria(commonMin = 1, totalWordsMin = 0, maxScoreRange = 0..1_000_000, maxAttempts = 1)
    val result = impossible.generate(seed = 1, size = 3, commonCutoff = Int.MAX_VALUE, criteria = criteria)
    (result.criteriaUsed.commonMin < criteria.commonMin) shouldBe true
  }

  test("Special-tile criteria relax too, so an unreachable threshold still terminates") {
    // The digraph table's letters (QU, CH...) never appear in smallLexicon(), so minWordsPerTile can
    // only ever be satisfied at 0: this proves relaxation reaches that, instead of spinning forever.
    val criteria = GenerationCriteria(
      commonMin = 0,
      totalWordsMin = 0,
      maxScoreRange = 0..1_000_000,
      maxAttempts = 1,
      specialTiles = SpecialTileCriteria(minWordsPerTile = 5, minWordsPerCorner = 5, minWordsPerOption = 5),
    )
    val result = generator().generate(seed = 1, size = 4, mutator = Mutator.Digraphs(2), criteria = criteria)
    (result.criteriaUsed.specialTiles.minWordsPerTile < criteria.specialTiles.minWordsPerTile) shouldBe true
  }

  test("Digraph tiles never land on a corner, for any seed and any count in 2..4") {
    (1L..15L).forEach { seed ->
      listOf(2, 3, 4).forEach { count ->
        val result = generator().generate(
          seed = seed,
          size = 4,
          mutator = Mutator.Digraphs(count),
          commonCutoff = Int.MAX_VALUE,
          criteria = reachableCriteria(),
        )
        val digraphPositions = result.board.tiles.indices.filter { result.board.tiles[it].letters.length == 2 }
        val corners = cornersOf(4).toSet()
        digraphPositions.forEach { position -> (position in corners) shouldBe false }
      }
    }
  }

  test("Digraph tiles are never adjacent to another digraph tile, for any seed and any count in 2..4") {
    (1L..15L).forEach { seed ->
      listOf(2, 3, 4).forEach { count ->
        val result = generator().generate(
          seed = seed,
          size = 4,
          mutator = Mutator.Digraphs(count),
          commonCutoff = Int.MAX_VALUE,
          criteria = reachableCriteria(),
        )
        val digraphPositions = result.board.tiles.indices.filter { result.board.tiles[it].letters.length == 2 }
        digraphPositions.forEachIndexed { index, a ->
          digraphPositions.drop(index + 1).forEach { b -> adjacent(4, a, b) shouldBe false }
        }
      }
    }
  }

  test("Every accepted board's special tiles meet the special-tile criteria that were actually used") {
    val cases = listOf(Mutator.ValuableLetter('C', 10), Mutator.Digraphs(3), Mutator.LetterInCorners('A'))
    cases.forEach { mutator ->
      (1L..10L).forEach { seed ->
        val result = generator().generate(
          seed = seed,
          size = 4,
          mutator = mutator,
          commonCutoff = Int.MAX_VALUE,
          criteria = reachableCriteria(),
        )
        val thresholds = result.criteriaUsed.specialTiles
        when (mutator) {
          is Mutator.ValuableLetter -> {
            val position = result.board.tiles.indexOfFirst { it.value == mutator.value }
            (wordsUsingTile(position, result.solution).size >= thresholds.minWordsPerTile) shouldBe true
          }
          is Mutator.Digraphs -> {
            val positions = result.board.tiles.indices.filter { result.board.tiles[it].letters.length == 2 }
            positions.forEach { position ->
              val words = wordsUsingTile(position, result.solution)
              (words.size >= thresholds.minWordsPerTile) shouldBe true
              if (thresholds.minWordsPerTile > 0) (words.any { it.tier == WordTier.Common }) shouldBe true
            }
          }
          is Mutator.LetterInCorners -> {
            cornersOf(4).forEach { corner ->
              (wordsUsingTile(corner, result.solution).size >= thresholds.minWordsPerCorner) shouldBe true
            }
          }
          else -> Unit
        }
      }
    }
  }

  test("OneOrOther places exactly one alternatives tile, at a non-edge position, worth 20 points") {
    (1L..15L).forEach { seed ->
      val result = generator().generate(
        seed = seed,
        size = 4,
        mutator = Mutator.OneOrOther('A', 'S'),
        commonCutoff = Int.MAX_VALUE,
        criteria = reachableCriteria(),
      )
      val alternatives = result.board.tiles.filter { it.letters.contains('/') }
      alternatives shouldHaveSize 1
      val tile = alternatives.single()
      tile.letters shouldBe "A/S"
      tile.value shouldBe BoardGenerator.OneOrOtherValue
      (result.board.tiles.indexOf(tile) in centersOf(4)) shouldBe true
    }
  }

  test("At least one seed meets the special-tile criteria without any relaxation, for every special-tile mutator") {
    val cases = listOf(
      generator() to Mutator.ValuableLetter('C', 10),
      digraphGenerator() to Mutator.Digraphs(2),
      generator() to Mutator.LetterInCorners('A'),
    )
    cases.forEach { (caseGenerator, mutator) ->
      val anyUnrelaxed = (1L..15L).any { seed ->
        val result = caseGenerator.generate(
          seed = seed,
          size = 4,
          mutator = mutator,
          commonCutoff = Int.MAX_VALUE,
          criteria = reachableCriteria(),
        )
        result.criteriaUsed.specialTiles == reachableCriteria().specialTiles
      }
      anyUnrelaxed shouldBe true
    }

    val anyUnrelaxedOneOrOther = (1L..15L).any { seed ->
      val result = oneOrOtherGenerator().generate(
        seed = seed,
        size = 4,
        mutator = Mutator.OneOrOther('A', 'S'),
        commonCutoff = Int.MAX_VALUE,
        criteria = reachableCriteria(),
      )
      result.criteriaUsed.specialTiles == reachableCriteria().specialTiles
    }
    anyUnrelaxedOneOrOther shouldBe true
  }

  test("OneOrOther's accepted board satisfies the injected minWordsPerOption for both options") {
    (1L..15L).forEach { seed ->
      val result = oneOrOtherGenerator().generate(
        seed = seed,
        size = 4,
        mutator = Mutator.OneOrOther('A', 'S'),
        commonCutoff = Int.MAX_VALUE,
        criteria = reachableCriteria(),
      )
      val thresholds = result.criteriaUsed.specialTiles
      val position = result.board.tiles.indexOfFirst { it.letters.contains('/') }
      val words = wordsUsingTile(position, result.solution)
      val firstCount = words.count { optionUsedAt(result.board, it, position) == 'A' }
      val secondCount = words.count { optionUsedAt(result.board, it, position) == 'S' }
      (words.size >= thresholds.minWordsPerTile) shouldBe true
      (firstCount >= thresholds.minWordsPerOption) shouldBe true
      (secondCount >= thresholds.minWordsPerOption) shouldBe true
    }
  }
})
