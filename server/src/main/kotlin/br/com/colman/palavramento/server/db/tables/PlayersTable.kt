// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.db.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/** Mirrors `V1__init.sql`'s `players` table (dossier §7 plus the auth columns §8 needs). */
object PlayersTable : Table("players") {
  val id = text("id")
  val displayName = text("display_name")
  val isGuest = bool("is_guest")
  val authProvider = text("auth_provider")
  val email = text("email").nullable().uniqueIndex()
  val passwordHash = text("password_hash").nullable()
  val createdAt = timestampWithTimeZone("created_at")

  override val primaryKey = PrimaryKey(id)
}
