// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.repository

import br.com.colman.palavramento.server.db.tables.PlayerStatsTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update

/** A `player_stats` row (dossier §7), registered players only (dossier §8). */
data class PlayerStatsRow(
  val playerId: String,
  val totalScore: Long,
  val totalWords: Long,
  val bestGameScore: Int,
  val bestWord: String?,
  val bestWordScore: Int,
  val gamesPlayed: Int,
  val gamesCompleted: Int,
  val bestRank: Int?,
  val totalXp: Long,
)

/** One round's contribution to a player's lifetime stats, as [PlayerStatsRepository.applyRound] folds it in. */
data class RoundContribution(
  val score: Int,
  val words: Int,
  val bestWordDisplay: String?,
  val bestWordScore: Int,
  val rank: Int,
  val xp: Int,
)

/** CRUD plus incremental aggregation over `player_stats` (dossier §7: "mantido incrementalmente"). */
class PlayerStatsRepository(private val database: Database) {

  suspend fun get(playerId: String): PlayerStatsRow? = suspendTransaction(database) {
    PlayerStatsTable.selectAll().where { PlayerStatsTable.playerId eq playerId }.singleOrNull()?.toRow()
  }

  suspend fun delete(playerId: String) = suspendTransaction(database) {
    PlayerStatsTable.deleteWhere { PlayerStatsTable.playerId eq playerId }
    Unit
  }

  /**
   * Same as [delete], but within an already-open [transaction] (account deletion, ADR 0020), for
   * callers composing several tables' deletes in one transaction.
   */
  fun delete(transaction: JdbcTransaction, playerId: String) {
    with(transaction) {
      PlayerStatsTable.deleteWhere { PlayerStatsTable.playerId eq playerId }
    }
  }

  /**
   * Folds [contribution] into [playerId]'s row within [transaction], creating it on the player's
   * first completed round. Runs inside a caller-supplied [JdbcTransaction] so it commits atomically
   * with the same round's `round_results` row (dossier §7).
   */
  fun applyRound(transaction: JdbcTransaction, playerId: String, contribution: RoundContribution) {
    with(transaction) {
      val existing = PlayerStatsTable.selectAll().where { PlayerStatsTable.playerId eq playerId }.singleOrNull()
      val completed = if (contribution.words > 0) 1 else 0
      if (existing == null) {
        PlayerStatsTable.insert {
          it[PlayerStatsTable.playerId] = playerId
          it[totalScore] = contribution.score.toLong()
          it[totalWords] = contribution.words.toLong()
          it[bestGameScore] = contribution.score
          it[bestWord] = contribution.bestWordDisplay
          it[bestWordScore] = contribution.bestWordScore
          it[gamesPlayed] = 1
          it[gamesCompleted] = completed
          it[bestRank] = contribution.rank
          it[totalXp] = contribution.xp.toLong()
        }
      } else {
        val currentBestWordScore = existing[PlayerStatsTable.bestWordScore]
        val currentBestRank = existing[PlayerStatsTable.bestRank]
        PlayerStatsTable.update({ PlayerStatsTable.playerId eq playerId }) {
          it[totalScore] = existing[PlayerStatsTable.totalScore] + contribution.score
          it[totalWords] = existing[PlayerStatsTable.totalWords] + contribution.words
          it[bestGameScore] = maxOf(existing[PlayerStatsTable.bestGameScore], contribution.score)
          if (contribution.bestWordScore > currentBestWordScore) {
            it[bestWord] = contribution.bestWordDisplay
            it[bestWordScore] = contribution.bestWordScore
          }
          it[gamesPlayed] = existing[PlayerStatsTable.gamesPlayed] + 1
          it[gamesCompleted] = existing[PlayerStatsTable.gamesCompleted] + completed
          it[bestRank] = if (currentBestRank == null) contribution.rank else minOf(currentBestRank, contribution.rank)
          it[totalXp] = existing[PlayerStatsTable.totalXp] + contribution.xp
        }
      }
    }
  }

  /** Replaces [playerId]'s row outright, e.g. rebuilding it from `round_results` on migration (ADR 0007). */
  suspend fun replace(row: PlayerStatsRow) = suspendTransaction(database) {
    PlayerStatsTable.deleteWhere { PlayerStatsTable.playerId eq row.playerId }
    PlayerStatsTable.insert {
      it[playerId] = row.playerId
      it[totalScore] = row.totalScore
      it[totalWords] = row.totalWords
      it[bestGameScore] = row.bestGameScore
      it[bestWord] = row.bestWord
      it[bestWordScore] = row.bestWordScore
      it[gamesPlayed] = row.gamesPlayed
      it[gamesCompleted] = row.gamesCompleted
      it[bestRank] = row.bestRank
      it[totalXp] = row.totalXp
    }
    Unit
  }

  private fun ResultRow.toRow() = PlayerStatsRow(
    playerId = this[PlayerStatsTable.playerId],
    totalScore = this[PlayerStatsTable.totalScore],
    totalWords = this[PlayerStatsTable.totalWords],
    bestGameScore = this[PlayerStatsTable.bestGameScore],
    bestWord = this[PlayerStatsTable.bestWord],
    bestWordScore = this[PlayerStatsTable.bestWordScore],
    gamesPlayed = this[PlayerStatsTable.gamesPlayed],
    gamesCompleted = this[PlayerStatsTable.gamesCompleted],
    bestRank = this[PlayerStatsTable.bestRank],
    totalXp = this[PlayerStatsTable.totalXp],
  )
}
