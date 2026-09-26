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
 * Fills a round's leaderboard with robots up to [minimumPlayers] (ADR 0024), so a player alone in
 * the room still sees a room with people in it. Robots are weak on purpose: each one is credited
 * with a handful of short common words, the ones any beginner finds first, so a human who plays at
 * all almost always finishes above them.
 */
object BotFiller {
  const val DefaultMinimumPlayers = 5

  /** A robot finds between these many words, inclusive. */
  const val MinWords = 3
  const val MaxWords = 10

  /** Longest word a robot finds, when the board has enough of them. */
  const val MaxEasyLength = 5

  /** How many robots [humans] need to reach [minimumPlayers]; none when they already do. */
  fun countFor(humans: Int, minimumPlayers: Int = DefaultMinimumPlayers): Int =
    (minimumPlayers - humans).coerceAtLeast(0)

  fun fill(
    humans: Int,
    solution: List<SolvedWord>,
    random: Random,
    minimumPlayers: Int = DefaultMinimumPlayers,
  ): List<BotPlayer> {
    val count = countFor(humans, minimumPlayers)
    val names = names(count, random)
    val pool = easyWords(solution)
    return (1..count).map { number ->
      val wordCount = random.nextInt(MinWords, MaxWords + 1).coerceAtMost(pool.size)
      BotPlayer("bot-$number", names[number - 1], pool.shuffled(random).take(wordCount))
    }
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

  private const val GuestSuffixLength = 4
  private const val HexDigits = "0123456789ABCDEF"

  internal val Nicknames = listOf(
    "Ana", "Bia", "Caio", "Duda", "Gabi", "Lu", "Rafa", "Nanda", "Téo", "Malu",
    "Juju", "Guto", "Lari", "Pedrinho", "Carol", "Dani", "Fer", "Bruno", "Manu", "Tati",
    "Vini", "Babi", "Rê", "Nando", "Cris", "Paty", "Zé", "Lipe", "Mari", "Tiago",
  )
}
