// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.repository

import br.com.colman.palavramento.server.db.tables.RoundResultsTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset

/**
 * A `round_results` row (dossier §7). [enteredAt] is when this player actually started playing the
 * round (ADR 0010: late join), used to compute their own `secondsPerWord` instead of the round's.
 */
data class RoundResultRow(
  val roundId: String,
  val playerId: String,
  val score: Int,
  val words: Int,
  val rank: Int,
  val xp: Int,
  val enteredAt: Instant,
  val doubleXp: Boolean = false,
)

/** CRUD over `round_results` (dossier §7). Writes happen inside [PlayerStatsRepository.applyRound]'s transaction. */
class RoundResultRepository(private val database: Database) {

  fun insert(transaction: JdbcTransaction, row: RoundResultRow) {
    with(transaction) {
      RoundResultsTable.insert {
        it[roundId] = row.roundId
        it[playerId] = row.playerId
        it[score] = row.score
        it[words] = row.words
        it[rank] = row.rank
        it[xp] = row.xp
        it[doubleXp] = row.doubleXp
        it[enteredAt] = row.enteredAt.atOffset(ZoneOffset.UTC)
      }
    }
  }

  suspend fun findByRound(roundId: String): List<RoundResultRow> = suspendTransaction(database) {
    RoundResultsTable.selectAll().where { RoundResultsTable.roundId eq roundId }.map { it.toRow() }
  }

  /** [playerId]'s round history, newest round first by join order upstream (dossier `/players/me/rounds`). */
  suspend fun findByPlayer(playerId: String, limit: Int): List<RoundResultRow> = suspendTransaction(database) {
    RoundResultsTable.selectAll()
      .where { RoundResultsTable.playerId eq playerId }
      .orderBy(RoundResultsTable.roundId, SortOrder.DESC)
      .limit(limit)
      .map { it.toRow() }
  }

  suspend fun countPlayers(roundId: String): Int = suspendTransaction(database) {
    RoundResultsTable.selectAll().where { RoundResultsTable.roundId eq roundId }.count().toInt()
  }

  suspend fun findByPlayerAll(playerId: String): List<RoundResultRow> = suspendTransaction(database) {
    RoundResultsTable.selectAll().where { RoundResultsTable.playerId eq playerId }.map { it.toRow() }
  }

  /**
   * Moves one round's result from [fromPlayerId] to [toPlayerId] (login migration, ADR 0007). Only
   * called when [toPlayerId] has no result for that round yet: the conflict case (both played the
   * same round) is resolved by the caller dropping the guest's row instead of calling this.
   */
  fun reassignRound(transaction: JdbcTransaction, roundId: String, fromPlayerId: String, toPlayerId: String) {
    with(transaction) {
      RoundResultsTable.update(
        { (RoundResultsTable.roundId eq roundId) and (RoundResultsTable.playerId eq fromPlayerId) }
      ) {
        it[playerId] = toPlayerId
      }
    }
  }

  /** Drops the losing side of a migration conflict: [fromPlayerId]'s duplicate result for [roundId]. */
  fun deleteRound(transaction: JdbcTransaction, roundId: String, playerId: String) {
    with(transaction) {
      RoundResultsTable.deleteWhere {
        (RoundResultsTable.roundId eq roundId) and (RoundResultsTable.playerId eq playerId)
      }
    }
  }

  private fun ResultRow.toRow() = RoundResultRow(
    roundId = this[RoundResultsTable.roundId],
    playerId = this[RoundResultsTable.playerId],
    score = this[RoundResultsTable.score],
    words = this[RoundResultsTable.words],
    rank = this[RoundResultsTable.rank],
    xp = this[RoundResultsTable.xp],
    doubleXp = this[RoundResultsTable.doubleXp],
    enteredAt = this[RoundResultsTable.enteredAt].toInstant(),
  )
}
