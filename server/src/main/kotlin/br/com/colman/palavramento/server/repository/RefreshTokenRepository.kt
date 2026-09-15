// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.repository

import br.com.colman.palavramento.server.db.tables.RefreshTokensTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset

/** A `refresh_tokens` row. Only [tokenHash] is stored, never the raw token (dossier §8). */
data class RefreshTokenRow(
  val id: String,
  val playerId: String,
  val tokenHash: String,
  val createdAt: Instant,
  val expiresAt: Instant,
  val revokedAt: Instant?,
  val replacedByHash: String?,
)

/**
 * CRUD over `refresh_tokens`, backing rotating-refresh-token auth (ADR 0007): each successful
 * `/auth/refresh` revokes the presented token and links it ([revoke]'s `replacedByHash`) to the one
 * that replaced it, so presenting an already-revoked token again is detectable as reuse.
 */
class RefreshTokenRepository(private val database: Database) {

  suspend fun insert(row: RefreshTokenRow) = suspendTransaction(database) {
    RefreshTokensTable.insert {
      it[id] = row.id
      it[playerId] = row.playerId
      it[tokenHash] = row.tokenHash
      it[createdAt] = row.createdAt.atOffset(ZoneOffset.UTC)
      it[expiresAt] = row.expiresAt.atOffset(ZoneOffset.UTC)
      it[revokedAt] = row.revokedAt?.atOffset(ZoneOffset.UTC)
      it[replacedByHash] = row.replacedByHash
    }
    Unit
  }

  suspend fun findByHash(tokenHash: String): RefreshTokenRow? = suspendTransaction(database) {
    RefreshTokensTable.selectAll().where { RefreshTokensTable.tokenHash eq tokenHash }.singleOrNull()?.toRow()
  }

  suspend fun revoke(tokenHash: String, replacedByHash: String?, revokedAt: Instant) = suspendTransaction(database) {
    RefreshTokensTable.update({ RefreshTokensTable.tokenHash eq tokenHash }) {
      it[RefreshTokensTable.revokedAt] = revokedAt.atOffset(ZoneOffset.UTC)
      it[RefreshTokensTable.replacedByHash] = replacedByHash
    }
    Unit
  }

  /** Revokes every still-active token for [playerId] (reuse detection: a stolen chain is cut off). */
  suspend fun revokeAllForPlayer(playerId: String, revokedAt: Instant) = suspendTransaction(database) {
    RefreshTokensTable.update({ RefreshTokensTable.playerId eq playerId }) {
      it[RefreshTokensTable.revokedAt] = revokedAt.atOffset(ZoneOffset.UTC)
    }
    Unit
  }

  /**
   * Deletes every token row for [playerId] outright, e.g. once its player row is about to be
   * deleted (login migration, ADR 0007): a revoked-but-still-present row would violate the
   * `players` foreign key.
   */
  suspend fun deleteAllForPlayer(playerId: String) = suspendTransaction(database) {
    RefreshTokensTable.deleteWhere { RefreshTokensTable.playerId eq playerId }
    Unit
  }

  private fun ResultRow.toRow() = RefreshTokenRow(
    id = this[RefreshTokensTable.id],
    playerId = this[RefreshTokensTable.playerId],
    tokenHash = this[RefreshTokensTable.tokenHash],
    createdAt = this[RefreshTokensTable.createdAt].toInstant(),
    expiresAt = this[RefreshTokensTable.expiresAt].toInstant(),
    revokedAt = this[RefreshTokensTable.revokedAt]?.toInstant(),
    replacedByHash = this[RefreshTokensTable.replacedByHash],
  )
}
