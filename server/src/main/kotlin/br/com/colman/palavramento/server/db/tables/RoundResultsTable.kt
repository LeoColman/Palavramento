// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.db.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/**
 * Mirrors `V1__init.sql`/`V2__round_results_entered_at.sql`'s `round_results` table. [doubleXp]
 * always false in v1 (dossier §9: "Get Double XP!" is out of v1, the column is only kept ready).
 * [enteredAt] is when this player actually started playing the round (ADR 0010: late join), `max`
 * of the round's own `starts_at` and their join time; equal to the round's `starts_at` for anyone
 * present when it started.
 */
object RoundResultsTable : Table("round_results") {
  val roundId = text("round_id").references(RoundsTable.id)
  val playerId = text("player_id").references(PlayersTable.id)
  val score = integer("score")
  val words = integer("words")
  val rank = integer("rank")
  val xp = integer("xp")
  val doubleXp = bool("double_xp").default(false)
  val enteredAt = timestampWithTimeZone("entered_at")

  override val primaryKey = PrimaryKey(roundId, playerId)
}
