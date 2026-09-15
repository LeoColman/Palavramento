// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.db.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/**
 * Mirrors `V1__init.sql`'s `rounds` table. `boardJson`/`mutatorJson` hold the serialized
 * `List<Tile>`/`Mutator` ([br.com.colman.palavramento.domain.protocol.PalavramentoJson]), so the
 * exact wire representation is what gets persisted and replayed, never a second hand-rolled mapping.
 */
object RoundsTable : Table("rounds") {
  val id = text("id")
  val roomId = text("room_id")
  val seed = long("seed")
  val boardJson = text("board_json")
  val mutatorJson = text("mutator_json")
  val themeTitle = text("theme_title")
  val themeSubtitle = text("theme_subtitle")
  val commonMin = integer("common_min")
  val maxScore = integer("max_score")
  val maxWords = integer("max_words")
  val startsAt = timestampWithTimeZone("starts_at")
  val endsAt = timestampWithTimeZone("ends_at")
  val status = text("status")

  override val primaryKey = PrimaryKey(id)
}
