// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.bot

import br.com.colman.palavramento.domain.solver.SolvedWord
import br.com.colman.palavramento.domain.solver.WordTier
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlin.random.Random

private fun word(normalized: String, score: Int, tier: WordTier = WordTier.Common) =
  SolvedWord(normalized, normalized.lowercase(), normalized.indices.toList(), score, tier)

/** Ten short common words, ten long common ones and ten expert ones: a board with a bit of everything. */
private fun board(): List<SolvedWord> =
  (1..10).map { word("CAS" + ('A' + it), it) } +
    (1..10).map { word("CASARAO" + ('A' + it), 20 + it) } +
    (1..10).map { word("XIS" + ('A' + it), 30 + it, WordTier.Expert) }

class BotFillerTest : FunSpec({

  test("robots fill the room up to the minimum, and never past it") {
    checkAll(Arb.int(0..12), Arb.int(0..8), Arb.long()) { humans, minimum, seed ->
      val bots = BotFiller.fill(humans, board(), Random(seed), minimum)
      bots shouldHaveSize maxOf(0, minimum - humans)
      bots.size shouldBe BotFiller.countFor(humans, minimum)
    }
  }

  test("the default minimum is five players") {
    BotFiller.DefaultMinimumPlayers shouldBe 5
    BotFiller.fill(1, board(), Random(1)) shouldHaveSize 4
    BotFiller.fill(5, board(), Random(1)) shouldHaveSize 0
    BotFiller.countFor(2) shouldBe 3
  }

  test("each robot finds a few distinct short common words from the board") {
    checkAll(Arb.long()) { seed ->
      val solution = board()
      BotFiller.fill(0, solution, Random(seed)).forEach { bot ->
        bot.words.size shouldBeInRange BotFiller.MinWords..BotFiller.MaxWords
        bot.words.distinct() shouldBe bot.words
        bot.words.forEach { found ->
          found shouldBeIn solution
          found.tier shouldBe WordTier.Common
          found.normalized.length shouldBeInRange 1..BotFiller.MaxEasyLength
        }
        bot.score shouldBe bot.words.sumOf { it.score }
      }
    }
  }

  test("a board with few short words falls back to common words, then to any word") {
    val fewShort = (1..3).map { word("SO" + ('A' + it), 1) } + (1..10).map { word("CASARAO" + ('A' + it), 20) }
    BotFiller.easyWords(fewShort) shouldBe fewShort

    val fewCommon = (1..3).map { word("SO" + ('A' + it), 1) } +
      (1..4).map { word("XIS" + ('A' + it), 9, WordTier.Expert) }
    BotFiller.easyWords(fewCommon) shouldBe fewCommon

    val plenty = board()
    BotFiller.easyWords(plenty) shouldBe plenty.take(10)
  }

  test("a board with exactly enough short common words keeps to them") {
    val exactly = (1..BotFiller.MaxWords).map { word("SO" + ('A' + it), 1) } + word("CASARAOS", 30)
    BotFiller.easyWords(exactly) shouldBe exactly.dropLast(1)
  }

  test("a board with no words still gives every robot a place, with nothing found") {
    val bots = BotFiller.fill(2, emptyList(), Random(7))
    bots shouldHaveSize 3
    bots.forEach {
      it.words shouldBe emptyList()
      it.score shouldBe 0
    }
  }

  test("robots have distinct names and ids, each a nickname or a guest's default name") {
    checkAll(Arb.long()) { seed ->
      val bots = BotFiller.fill(0, board(), Random(seed), minimumPlayers = 8)
      bots.map { it.name }.distinct() shouldHaveSize 8
      bots.map { it.id } shouldBe (1..8).map { "bot-$it" }
      bots.forEach { bot ->
        if (bot.name !in BotFiller.Nicknames) bot.name shouldMatch Regex("Convidado [0-9A-F]{4}")
      }
    }
  }

  test("names mix nicknames and guest names") {
    val names = (1L..50L).flatMap { seed -> BotFiller.names(4, Random(seed)) }
    names.filter { it in BotFiller.Nicknames }.shouldNotBeEmpty()
    names.filter { it.startsWith("Convidado ") }.shouldNotBeEmpty()
  }

  test("more robots than nicknames still get distinct names") {
    val names = BotFiller.names(BotFiller.Nicknames.size + 5, Random(3))
    names.distinct() shouldHaveSize BotFiller.Nicknames.size + 5
  }

  test("the same seed gives the same robots") {
    BotFiller.fill(1, board(), Random(42)) shouldBe BotFiller.fill(1, board(), Random(42))
  }
})
