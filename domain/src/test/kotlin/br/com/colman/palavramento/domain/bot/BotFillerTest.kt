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

/** A big board: 80 common words, 60 of them short, and 20 expert ones. */
private fun bigBoard(): List<SolvedWord> =
  (1..60).map { word("C" + it.toString().padStart(3, '0'), 2) } +
    (1..20).map { word("CASARAO" + it.toString().padStart(3, '0'), 12) } +
    (1..20).map { word("X" + it.toString().padStart(3, '0'), 20, WordTier.Expert) }

/** What [BotFiller.names] gives for six names from `Random(7)`: pins which coin side picks a nickname. */
private val GoldenNames =
  listOf("Ana", "Convidado 3944", "Convidado 2D94", "Convidado 0F3A", "Cris", "Paty")

private fun List<BotPlayer>.good() = filter { it.words.size >= BotFiller.GoodMinWords }

class BotFillerTest : FunSpec({

  test("a lone player gets between two and five robots, and sees every count in that range") {
    val counts = (1L..400L).map { seed -> BotFiller.fill(1, board(), Random(seed)).size }
    counts.forEach { it shouldBeInRange BotFiller.DefaultMinBots..BotFiller.DefaultMaxBots }
    counts.toSet() shouldBe (2..5).toSet()
    BotFiller.DefaultMinBots shouldBe 2
    BotFiller.DefaultMaxBots shouldBe 5
  }

  test("each person beyond the first replaces one robot, and a full room has none") {
    checkAll(Arb.int(1..12), Arb.int(0..6), Arb.long()) { humans, quota, seed ->
      BotFiller.countFor(humans, quota) shouldBe maxOf(0, quota - (humans - 1))
      val alone = BotFiller.fill(1, board(), Random(seed)).size
      BotFiller.fill(humans, board(), Random(seed)).size shouldBe maxOf(0, alone - (humans - 1))
    }
    BotFiller.countFor(humans = 0, quota = 3) shouldBe 3
    BotFiller.countFor(humans = 6, quota = 5) shouldBe 0
  }

  test("the quota stays inside its bounds, a bound of zero turns robots off, and a min above the max yields") {
    checkAll(Arb.int(0..6), Arb.int(0..6), Arb.long()) { min, max, seed ->
      val quota = BotFiller.quota(Random(seed), min, max)
      if (max == 0) quota shouldBe 0 else quota shouldBeInRange minOf(min, max)..max
    }
    BotFiller.fill(1, board(), Random(1), minBots = 0, maxBots = 0) shouldHaveSize 0
    BotFiller.quota(Random(1), minBots = 3, maxBots = 3) shouldBe 3
  }

  test("each weak robot finds a few distinct short common words from the board") {
    checkAll(Arb.long()) { seed ->
      val solution = board()
      BotFiller.fill(1, solution, Random(seed)).filter { it.words.size <= BotFiller.MaxWords }.forEach { bot ->
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
    val bots = BotFiller.fill(1, emptyList(), Random(7), minBots = 3, maxBots = 3)
    bots shouldHaveSize 3
    bots.forEach {
      it.words shouldBe emptyList()
      it.score shouldBe 0
    }
  }

  test("robots have distinct names and ids, each a nickname or a guest's default name") {
    checkAll(Arb.long()) { seed ->
      val bots = BotFiller.fill(1, board(), Random(seed), minBots = 8, maxBots = 8)
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

  test("about one round in five with robots has a good one, and never more than one") {
    val rounds = (1L..2000L).map { seed -> BotFiller.fill(1, bigBoard(), Random(seed)) }
    rounds.forEach { it.good().size shouldBeInRange 0..1 }
    val withGood = rounds.count { it.good().size == 1 }
    withGood shouldBeInRange 330..470
    BotFiller.GoodBotOdds shouldBe 5
  }

  test("a good robot finds 40 to 60 distinct common words, most often near 40") {
    val good = (1L..3000L).flatMap { seed -> BotFiller.fill(1, bigBoard(), Random(seed)).good() }
    good.forEach { bot ->
      bot.words.size shouldBeInRange BotFiller.GoodMinWords..BotFiller.GoodMaxWords
      bot.words.distinct() shouldBe bot.words
      bot.words.forEach { it.tier shouldBe WordTier.Common }
    }
    val counts = good.map { it.words.size }.sorted()
    counts[counts.size / 2] shouldBeInRange 43..47
    counts.count { it <= 45 } shouldBeInRange counts.size * 45 / 100..counts.size * 62 / 100
    counts.count { it > 58 } shouldBeInRange 0..counts.size / 10
  }

  test("the good word count spans its whole range and stays inside it") {
    val counts = (1L..20_000L).map { BotFiller.goodWordCount(Random(it)) }
    counts.min() shouldBe BotFiller.GoodMinWords
    counts.max() shouldBe BotFiller.GoodMaxWords
    BotFiller.GoodMinWords shouldBe 40
    BotFiller.GoodMaxWords shouldBe 60
  }

  test("a good robot reaches past the common words only on a board without enough of them") {
    val plenty = bigBoard()
    BotFiller.goodWords(plenty) shouldBe plenty.filter { it.tier == WordTier.Common }

    val scarce = board()
    BotFiller.goodWords(scarce) shouldBe scarce

    val exactly = (1..BotFiller.GoodMaxWords).map { word("C" + it.toString().padStart(3, '0'), 1) } +
      word("XIS", 9, WordTier.Expert)
    BotFiller.goodWords(exactly) shouldBe exactly.dropLast(1)
  }

  test("on a small board a good robot takes what there is") {
    val good = (1L..500L).flatMap { seed -> BotFiller.fill(1, board(), Random(seed)) }
      .filter { it.words.size > BotFiller.MaxWords }
    good.shouldNotBeEmpty()
    good.forEach { it.words.size shouldBe board().size }
  }

  test("a five-letter word still counts as short") {
    val fiveLetters = (1..BotFiller.MaxWords).map { word("CASA" + ('A' + it), 1) } + word("CASARAO", 9)
    BotFiller.easyWords(fiveLetters) shouldBe fiveLetters.dropLast(1)
  }

  test("exactly enough common words is enough, even when few of them are short") {
    val common = (1..BotFiller.MaxWords).map { word("CASARAO" + ('A' + it), 9) } +
      word("XIS", 9, WordTier.Expert)
    BotFiller.easyWords(common) shouldBe common.dropLast(1)
  }

  test("a negative maximum turns robots off instead of failing") {
    BotFiller.quota(Random(1), minBots = 2, maxBots = -1) shouldBe 0
  }

  test("names come out the same for the same seed, nickname or guest name alike") {
    BotFiller.names(4, Random(42)) shouldBe BotFiller.names(4, Random(42))
    BotFiller.names(6, Random(7)) shouldBe GoldenNames
  }
})
