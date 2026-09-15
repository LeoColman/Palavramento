// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.db.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Mirrors `V1__init.sql`'s `player_stats` table: lifetime aggregates, registered players only
 * (dossier §8). Updated incrementally at round end, in the same transaction as [RoundResultsTable]
 * (dossier §7), and rebuilt from scratch on guest promotion/migration (dossier §8, ADR 0007).
 */
object PlayerStatsTable : Table("player_stats") {
  val playerId = text("player_id").references(PlayersTable.id)
  val totalScore = long("total_score")
  val totalWords = long("total_words")
  val bestGameScore = integer("best_game_score")
  val bestWord = text("best_word").nullable()
  val bestWordScore = integer("best_word_score")
  val gamesPlayed = integer("games_played")
  val gamesCompleted = integer("games_completed")
  val bestRank = integer("best_rank").nullable()
  val totalXp = long("total_xp")

  override val primaryKey = PrimaryKey(playerId)
}
