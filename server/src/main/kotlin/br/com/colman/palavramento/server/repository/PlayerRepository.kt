// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.repository

import br.com.colman.palavramento.server.db.tables.PlayersTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.ZoneOffset

/** A `players` row (dossier §7/§8). */
data class PlayerRow(
  val id: String,
  val displayName: String,
  val isGuest: Boolean,
  val authProvider: String,
  val email: String?,
  val passwordHash: String?,
  val createdAt: Instant,
)

const val GuestAuthProvider = "GUEST"
const val PasswordAuthProvider = "PASSWORD"

/** CRUD over `players` (dossier §7/§8). */
class PlayerRepository(private val database: Database) {

  suspend fun insert(row: PlayerRow) = suspendTransaction(database) {
    PlayersTable.insert {
      it[id] = row.id
      it[displayName] = row.displayName
      it[isGuest] = row.isGuest
      it[authProvider] = row.authProvider
      it[email] = row.email
      it[passwordHash] = row.passwordHash
      it[createdAt] = row.createdAt.atOffset(ZoneOffset.UTC)
    }
    Unit
  }

  suspend fun findById(id: String): PlayerRow? = suspendTransaction(database) {
    PlayersTable.selectAll().where { PlayersTable.id eq id }.singleOrNull()?.toPlayerRow()
  }

  suspend fun findByIds(ids: Collection<String>): Map<String, PlayerRow> = suspendTransaction(database) {
    if (ids.isEmpty()) {
      emptyMap()
    } else {
      PlayersTable.selectAll()
        .where { PlayersTable.id inList ids }
        .associate { it[PlayersTable.id] to it.toPlayerRow() }
    }
  }

  suspend fun findByEmail(email: String): PlayerRow? = suspendTransaction(database) {
    PlayersTable.selectAll().where { PlayersTable.email eq email }.singleOrNull()?.toPlayerRow()
  }

  /** Promotes a guest in place (dossier §8: "promove o convidado, migrando o historico"). */
  suspend fun promote(playerId: String, email: String, passwordHash: String, displayName: String) =
    suspendTransaction(database) {
      PlayersTable.update({ PlayersTable.id eq playerId }) {
        it[PlayersTable.email] = email
        it[PlayersTable.passwordHash] = passwordHash
        it[PlayersTable.displayName] = displayName
        it[PlayersTable.isGuest] = false
        it[PlayersTable.authProvider] = PasswordAuthProvider
      }
      Unit
    }

  suspend fun updateDisplayName(playerId: String, displayName: String) = suspendTransaction(database) {
    PlayersTable.update({ PlayersTable.id eq playerId }) { it[PlayersTable.displayName] = displayName }
    Unit
  }

  /** Removes a guest row once its history has been fully migrated onto another player (login migration). */
  suspend fun delete(playerId: String) = suspendTransaction(database) {
    PlayersTable.deleteWhere { PlayersTable.id eq playerId }
    Unit
  }

  /** Runs [block] with an already-open [JdbcTransaction], for callers composing multi-table writes. */
  suspend fun <T> transaction(block: suspend JdbcTransaction.() -> T): T = suspendTransaction(
    database,
    statement = block
  )

  private fun ResultRow.toPlayerRow() = PlayerRow(
    id = this[PlayersTable.id],
    displayName = this[PlayersTable.displayName],
    isGuest = this[PlayersTable.isGuest],
    authProvider = this[PlayersTable.authProvider],
    email = this[PlayersTable.email],
    passwordHash = this[PlayersTable.passwordHash],
    createdAt = this[PlayersTable.createdAt].toInstant(),
  )
}
