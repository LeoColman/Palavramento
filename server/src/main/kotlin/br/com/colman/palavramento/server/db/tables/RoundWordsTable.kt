// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.db.tables

import org.jetbrains.exposed.v1.core.Table

/** Mirrors `V1__init.sql`'s `round_words` table: a round's full pre-computed solution. */
object RoundWordsTable : Table("round_words") {
  val roundId = text("round_id").references(RoundsTable.id)
  val normalized = text("normalized")
  val word = text("word")
  val score = integer("score")
  val tier = text("tier")
  val pathJson = text("path_json")

  override val primaryKey = PrimaryKey(roundId, normalized)
}
