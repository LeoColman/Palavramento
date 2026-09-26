// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.round

import br.com.colman.palavramento.domain.bot.BotPlayer
import br.com.colman.palavramento.domain.stats.AcceptedWord
import br.com.colman.palavramento.domain.stats.LeaderboardEntry
import br.com.colman.palavramento.domain.stats.RankedEntry
import br.com.colman.palavramento.domain.stats.Ranking
import br.com.colman.palavramento.domain.stats.RoundStats
import br.com.colman.palavramento.domain.stats.RoundStatsCalculator
import br.com.colman.palavramento.domain.stats.XpFormula
import br.com.colman.palavramento.server.repository.PlayerRepository
import br.com.colman.palavramento.server.repository.PlayerRow
import br.com.colman.palavramento.server.repository.PlayerStatsRepository
import br.com.colman.palavramento.server.repository.RoundContribution
import br.com.colman.palavramento.server.repository.RoundResultRepository
import br.com.colman.palavramento.server.repository.RoundResultRow
import java.time.Instant

/** One participant's finished-round outcome, everything [ServerMessage.RoundEnd]/[Leaderboard] need. */
data class PlayerRoundOutcome(
  val playerId: String,
  val displayName: String,
  val stats: RoundStats,
  val rank: Int,
  val bestWord: FoundWord?,
)

/**
 * A finished round: [outcomes] for the people who played it, [leaderboard] for everyone it ranks,
 * robots included (ADR 0024), and [bots] for how many of those are robots.
 */
data class FinalizeResult(
  val outcomes: List<PlayerRoundOutcome>,
  val leaderboard: List<RankedEntry>,
  val bots: Int,
) {
  val humans: Int get() = outcomes.size
  val totalPlayers: Int get() = leaderboard.size

  companion object {
    val Empty = FinalizeResult(emptyList(), emptyList(), 0)
  }
}

/**
 * Turns one finished round's in-memory found-word state into persisted results (dossier §7): ranks
 * every participant, computes [RoundStats] and XP, then writes `round_results` and updates
 * `player_stats` for registered players in a single transaction, exactly like the dossier requires.
 *
 * Robots (ADR 0024) are ranked alongside the people, so a person's rank, percentile and history all
 * count them, but nothing about a robot is written: it has no player row and no stats to keep.
 */
class RoundFinalizer(
  private val playerRepository: PlayerRepository,
  private val roundResultRepository: RoundResultRepository,
  private val playerStatsRepository: PlayerStatsRepository,
  private val xpFormula: XpFormula = XpFormula.Default,
) {

  suspend fun finalize(
    roundId: String,
    perPlayerFound: Map<String, List<FoundWord>>,
    perPlayerEnteredAt: Map<String, Instant>,
    // How many people ended up taking part in, what robots join them. Asked only once the people
    // are known, after dropping deleted accounts, so the room is filled to the right size.
    bots: (humans: Int) -> List<BotPlayer> = { emptyList() },
  ): FinalizeResult {
    if (perPlayerFound.isEmpty()) return FinalizeResult.Empty

    val players = playerRepository.findByIds(perPlayerFound.keys)
    // A player who deleted their account while the round was running (ADR 0020) is already gone
    // from `players`, and `round_results` has a foreign key to it, so writing their row throws.
    // That exception used to travel out of [RoomScheduler.finish] and out of its round loop, which
    // left the room unable to ever start another round until someone restarted the server. They
    // have no business in anyone's leaderboard either, so they leave the round altogether.
    val participants = perPlayerFound.filterKeys { it in players }
    return if (participants.isEmpty()) {
      FinalizeResult.Empty
    } else {
      persist(roundId, participants, perPlayerEnteredAt, players, bots(participants.size))
    }
  }

  private suspend fun persist(
    roundId: String,
    participants: Map<String, List<FoundWord>>,
    perPlayerEnteredAt: Map<String, Instant>,
    players: Map<String, PlayerRow>,
    bots: List<BotPlayer>,
  ): FinalizeResult {
    val entries = participants.map { (playerId, words) ->
      LeaderboardEntry(playerId, players.getValue(playerId).displayName, words.sumOf { it.score }, words.size)
    }
    val botEntries = bots.map { LeaderboardEntry(it.id, it.name, it.score, it.words.size) }
    val ranked = Ranking.rank(entries + botEntries)

    val outcomes = ranked.filter { it.entry.playerId in participants }.map { rankedEntry ->
      val playerId = rankedEntry.entry.playerId
      val words = participants.getValue(playerId)
      val accepted = words.map { AcceptedWord(it.score, it.normalized.length, it.acceptedAt.toEpochMilli()) }
      // Measured from this player's own entry time (ADR 0010: a late joiner's secondsPerWord is
      // never inflated by time elapsed before they joined), not the round's own startsAt.
      val enteredAt = perPlayerEnteredAt.getValue(playerId)
      val stats = RoundStatsCalculator.compute(enteredAt.toEpochMilli(), accepted, xpFormula = xpFormula)
      val bestWord = words.maxByOrNull { it.score }
      PlayerRoundOutcome(playerId, rankedEntry.entry.name, stats, rankedEntry.rank, bestWord)
    }

    playerRepository.transaction {
      outcomes.forEach { outcome ->
        roundResultRepository.insert(
          this,
          RoundResultRow(
            roundId,
            outcome.playerId,
            outcome.stats.points,
            outcome.stats.words,
            outcome.rank,
            outcome.stats.xp,
            perPlayerEnteredAt.getValue(outcome.playerId),
          ),
        )
        // Non-null by construction: `participants` kept only the ids `players` answered for.
        if (!players.getValue(outcome.playerId).isGuest) {
          playerStatsRepository.applyRound(
            this,
            outcome.playerId,
            RoundContribution(
              score = outcome.stats.points,
              words = outcome.stats.words,
              bestWordDisplay = outcome.bestWord?.display,
              bestWordScore = outcome.bestWord?.score ?: 0,
              rank = outcome.rank,
              xp = outcome.stats.xp,
            ),
          )
        }
      }
    }

    return FinalizeResult(outcomes, ranked, bots.size)
  }
}
