// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.db.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/** Mirrors `V1__init.sql`'s `submissions` table: every accepted `SubmitWord` (dossier §7). */
object SubmissionsTable : Table("submissions") {
  val id = text("id")
  val roundId = text("round_id").references(RoundsTable.id)
  val playerId = text("player_id").references(PlayersTable.id)
  val normalized = text("normalized")
  val word = text("word")
  val score = integer("score")
  val pathJson = text("path_json")
  val acceptedAt = timestampWithTimeZone("accepted_at")

  override val primaryKey = PrimaryKey(id)
}
