// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.repository

import br.com.colman.palavramento.domain.protocol.PalavramentoJson
import br.com.colman.palavramento.server.db.tables.SubmissionsTable
import br.com.colman.palavramento.server.round.AcceptedSubmission
import br.com.colman.palavramento.server.round.FoundWord
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.ZoneOffset
import java.util.UUID

/** Persists and reloads accepted `SubmitWord`s (`submissions` table, dossier §7). */
class SubmissionRepository(private val database: Database) {

  suspend fun insert(submission: AcceptedSubmission) = suspendTransaction(database) {
    SubmissionsTable.insert {
      it[id] = UUID.randomUUID().toString()
      it[roundId] = submission.roundId
      it[playerId] = submission.playerId
      it[normalized] = submission.normalized
      it[word] = submission.display
      it[score] = submission.score
      it[pathJson] = PalavramentoJson.encodeToString(submission.path)
      it[acceptedAt] = submission.acceptedAt.atOffset(ZoneOffset.UTC)
    }
    Unit
  }

  /** Every word [playerId] had already found in [roundId], oldest first (dossier §5.3 reconnection). */
  suspend fun findByRoundAndPlayer(roundId: String, playerId: String): List<FoundWord> = suspendTransaction(database) {
    SubmissionsTable.selectAll()
      .where { (SubmissionsTable.roundId eq roundId) and (SubmissionsTable.playerId eq playerId) }
      .orderBy(SubmissionsTable.acceptedAt)
      .map { it.toFoundWord() }
  }

  /** Every accepted word in [roundId], grouped by player, for round-end stats and word labelling. */
  suspend fun findByRound(roundId: String): Map<String, List<FoundWord>> = suspendTransaction(database) {
    SubmissionsTable.selectAll()
      .where { SubmissionsTable.roundId eq roundId }
      .orderBy(SubmissionsTable.acceptedAt)
      .toList()
      .groupBy({ it[SubmissionsTable.playerId] }, { it.toFoundWord() })
  }

  /** Moves every submission for [roundId] from [fromPlayerId] to [toPlayerId] (login migration, ADR 0007). */
  fun reassignRound(transaction: JdbcTransaction, roundId: String, fromPlayerId: String, toPlayerId: String) {
    with(transaction) {
      SubmissionsTable.update(
        { (SubmissionsTable.roundId eq roundId) and (SubmissionsTable.playerId eq fromPlayerId) }
      ) {
        it[playerId] = toPlayerId
      }
    }
  }

  /** Drops the losing side of a migration conflict: [fromPlayerId]'s submissions for [roundId]. */
  fun deleteRound(transaction: JdbcTransaction, roundId: String, playerId: String) {
    with(transaction) {
      SubmissionsTable.deleteWhere { (SubmissionsTable.roundId eq roundId) and (SubmissionsTable.playerId eq playerId) }
    }
  }

  /** Deletes every submission [playerId] ever made, across every round (account deletion, ADR 0020). */
  fun deleteAllForPlayer(transaction: JdbcTransaction, playerId: String) {
    with(transaction) {
      SubmissionsTable.deleteWhere { SubmissionsTable.playerId eq playerId }
    }
  }

  private fun ResultRow.toFoundWord() = FoundWord(
    normalized = this[SubmissionsTable.normalized],
    display = this[SubmissionsTable.word],
    score = this[SubmissionsTable.score],
    path = PalavramentoJson.decodeFromString(this[SubmissionsTable.pathJson]),
    acceptedAt = this[SubmissionsTable.acceptedAt].toInstant(),
  )
}
