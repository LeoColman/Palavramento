// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.db.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/** Mirrors `V1__init.sql`'s `refresh_tokens` table. Only a hash of the token is ever stored. */
object RefreshTokensTable : Table("refresh_tokens") {
  val id = text("id")
  val playerId = text("player_id").references(PlayersTable.id)
  val tokenHash = text("token_hash").uniqueIndex()
  val createdAt = timestampWithTimeZone("created_at")
  val expiresAt = timestampWithTimeZone("expires_at")
  val revokedAt = timestampWithTimeZone("revoked_at").nullable()
  val replacedByHash = text("replaced_by_hash").nullable()

  override val primaryKey = PrimaryKey(id)
}
