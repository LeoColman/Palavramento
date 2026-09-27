// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.bot

import br.com.colman.palavramento.domain.solver.SolvedWord
import br.com.colman.palavramento.domain.solver.WordTier
import kotlin.random.Random

/**
 * A robot in a round's leaderboard (ADR 0024): the [name] the leaderboard shows and the [words] it
 * is credited with. [id] only exists so [br.com.colman.palavramento.domain.stats.Ranking] can break
 * ties; it never reaches the wire or the database.
 */
data class BotPlayer(val id: String, val name: String, val words: List<SolvedWord>) {
  val score: Int get() = words.sumOf { it.score }
}

/**
 * Puts robots in a round's leaderboard (ADR 0024), so a player alone in the room still sees a room
 * with people in it. Each round draws how many robots a lone player would see, between [minBots] and
 * [maxBots], and every other person in the round takes one robot's place, so a busy round has none.
 *
 * Robots are mostly weak: each one is credited with a handful of short common words, the ones any
 * beginner finds first, so a human who plays at all almost always finishes above them. But one round
 * in [GoodBotOdds] has a good robot among them, one that finds [GoodMinWords] to [GoodMaxWords]
 * common words, most often near the low end, so there is somebody worth beating now and then.
 */
object BotFiller {
  const val DefaultMinBots = 2
  const val DefaultMaxBots = 5

  /** A weak robot finds between these many words, inclusive, every count equally likely. */
  const val MinWords = 5
  const val MaxWords = 20

  /** Longest word a robot finds, when the board has enough of them. */
  const val MaxEasyLength = 5

  /** One round in this many that has robots has a good one among them. */
  const val GoodBotOdds = 5

  /** A good robot finds between these many words, inclusive, most often near [GoodMinWords]. */
  const val GoodMinWords = 40
  const val GoodMaxWords = 60

  /** How many robots a lone player would see this round: somewhere in [minBots]..[maxBots]. */
  fun quota(random: Random, minBots: Int = DefaultMinBots, maxBots: Int = DefaultMaxBots): Int {
    val max = maxBots.coerceAtLeast(0)
    return random.nextInt(minBots.coerceIn(0, max), max + 1)
  }

  /** [quota] robots for one person; each person beyond the first replaces one of them. */
  fun countFor(humans: Int, quota: Int): Int = (quota - (humans - 1).coerceAtLeast(0)).coerceAtLeast(0)

  fun fill(
    humans: Int,
    solution: List<SolvedWord>,
    random: Random,
    minBots: Int = DefaultMinBots,
    maxBots: Int = DefaultMaxBots,
  ): List<BotPlayer> {
    val count = countFor(humans, quota(random, minBots, maxBots))
    val names = names(count, random)
    val good = if (count > 0 && random.nextInt(GoodBotOdds) == 0) random.nextInt(1, count + 1) else NoGoodBot
    val easyPool = easyWords(solution)
    val goodPool = goodWords(solution)
    return (1..count).map { number ->
      val (pool, wordCount) = if (number == good) {
        goodPool to goodWordCount(random)
      } else {
        easyPool to random.nextInt(MinWords, MaxWords + 1)
      }
      BotPlayer("bot-$number", names[number - 1], pool.shuffled(random).take(wordCount.coerceAtMost(pool.size)))
    }
  }

  /**
   * [GoodMinWords] plus the range stretched by the square of a uniform draw: squaring keeps most draws
   * small, so about half the good robots find 45 words or fewer and only one in twenty gets past 58.
   */
  internal fun goodWordCount(random: Random): Int {
    val u = random.nextDouble()
    return GoodMinWords + ((GoodMaxWords - GoodMinWords + 1) * u * u).toInt().coerceAtMost(GoodMaxWords - GoodMinWords)
  }

  /** Common words of any length, and the expert ones too on a board with too few common words. */
  internal fun goodWords(solution: List<SolvedWord>): List<SolvedWord> {
    val common = solution.filter { it.tier == WordTier.Common }
    return if (common.size >= GoodMaxWords) common else solution
  }

  /** Short common words, falling back to any common word, then to any word, on a stingy board. */
  internal fun easyWords(solution: List<SolvedWord>): List<SolvedWord> {
    val common = solution.filter { it.tier == WordTier.Common }
    val short = common.filter { it.normalized.length <= MaxEasyLength }
    return when {
      short.size >= MaxWords -> short
      common.size >= MaxWords -> common
      else -> solution
    }
  }

  /**
   * [count] distinct names, each either a nickname or a guest's default name ("Convidado" and four
   * hex digits, as the server names a guest), since that is what a real room's leaderboard is made of.
   */
  internal fun names(count: Int, random: Random): List<String> {
    val nicknames = Nicknames.shuffled(random).iterator()
    val names = LinkedHashSet<String>()
    while (names.size < count) {
      names += if (random.nextBoolean() && nicknames.hasNext()) nicknames.next() else guestName(random)
    }
    return names.toList()
  }

  private fun guestName(random: Random): String =
    "Convidado " + (1..GuestSuffixLength).joinToString("") { HexDigits[random.nextInt(HexDigits.length)].toString() }

  private const val NoGoodBot = 0
  private const val GuestSuffixLength = 4
  private const val HexDigits = "0123456789ABCDEF"

  internal val Nicknames = listOf(
    "Ana", "Bia", "Caio", "Duda", "Gabi", "Lu", "Rafa", "Nanda", "Téo", "Malu",
    "Juju", "Guto", "Lari", "Pedrinho", "Carol", "Dani", "Fer", "Bruno", "Manu", "Tati",
    "Vini", "Babi", "Rê", "Nando", "Cris", "Paty", "Zé", "Lipe", "Mari", "Tiago",
  )
}
