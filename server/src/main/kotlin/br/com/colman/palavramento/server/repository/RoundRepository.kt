// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.repository

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.protocol.PalavramentoJson
import br.com.colman.palavramento.domain.solver.SolvedWord
import br.com.colman.palavramento.domain.solver.WordTier
import br.com.colman.palavramento.server.db.tables.RoundWordsTable
import br.com.colman.palavramento.server.db.tables.RoundsTable
import br.com.colman.palavramento.server.round.RoundRecord
import br.com.colman.palavramento.server.round.RoundStatus
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.sqrt

/** How many people and how many robots took part in a finished round (ADR 0024). */
data class RoundPlayerCounts(val humans: Int, val bots: Int) {
  val total: Int get() = humans + bots
}

/** Persists and reloads rounds and their pre-computed solutions (`rounds`/`round_words`, dossier §7). */
class RoundRepository(private val database: Database) {

  /** Persists [round]'s row and its full solution in one transaction (dossier §3: before it starts). */
  suspend fun insert(round: RoundRecord, solution: List<SolvedWord>) = suspendTransaction(database) {
    RoundsTable.insert {
      it[id] = round.id
      it[roomId] = round.roomId
      it[seed] = round.seed
      it[boardJson] = PalavramentoJson.encodeToString(round.board.tiles)
      it[mutatorJson] = PalavramentoJson.encodeToString(round.mutator)
      it[themeTitle] = round.themeTitle
      it[themeSubtitle] = round.themeSubtitle
      it[commonMin] = round.commonMin
      it[maxScore] = round.maxScore
      it[maxWords] = round.maxWords
      it[startsAt] = round.startsAt.toOffset()
      it[endsAt] = round.endsAt.toOffset()
      it[status] = round.status
    }
    RoundWordsTable.batchInsert(solution) { word ->
      this[RoundWordsTable.roundId] = round.id
      this[RoundWordsTable.normalized] = word.normalized
      this[RoundWordsTable.word] = word.display
      this[RoundWordsTable.score] = word.score
      this[RoundWordsTable.tier] = word.tier.name
      this[RoundWordsTable.pathJson] = PalavramentoJson.encodeToString(word.path)
    }
    Unit
  }

  suspend fun updateStatus(roundId: String, status: String) = suspendTransaction(database) {
    RoundsTable.update({ RoundsTable.id eq roundId }) { it[RoundsTable.status] = status }
    Unit
  }

  /** Records who took part in finished round [roundId], people and robots apart (ADR 0024). */
  suspend fun recordPlayerCounts(roundId: String, counts: RoundPlayerCounts) = suspendTransaction(database) {
    RoundsTable.update({ RoundsTable.id eq roundId }) {
      it[humanPlayers] = counts.humans
      it[botPlayers] = counts.bots
    }
    Unit
  }

  /** What [recordPlayerCounts] wrote for [roundId], or null for a round finished before it existed. */
  suspend fun findPlayerCounts(roundId: String): RoundPlayerCounts? = suspendTransaction(database) {
    RoundsTable.select(RoundsTable.humanPlayers, RoundsTable.botPlayers)
      .where { RoundsTable.id eq roundId }
      .singleOrNull()
      ?.toPlayerCounts()
  }

  /** The counts of [roomId]'s most recently started round that has them: the metrics' "now". */
  suspend fun findLatestPlayerCounts(roomId: String): RoundPlayerCounts? = suspendTransaction(database) {
    RoundsTable.select(RoundsTable.humanPlayers, RoundsTable.botPlayers)
      .where { (RoundsTable.roomId eq roomId) and RoundsTable.humanPlayers.isNotNull() }
      .orderBy(RoundsTable.startsAt, SortOrder.DESC)
      .limit(1)
      .singleOrNull()
      ?.toPlayerCounts()
  }

  suspend fun findById(roundId: String): RoundRecord? = suspendTransaction(database) {
    RoundsTable.selectAll().where { RoundsTable.id eq roundId }.singleOrNull()?.toRoundRecord()
  }

  /** Most recently starting round for [roomId], regardless of status, or null if none exists yet. */
  suspend fun findLatest(roomId: String): RoundRecord? = suspendTransaction(database) {
    RoundsTable.selectAll()
      .where { RoundsTable.roomId eq roomId }
      .orderBy(RoundsTable.startsAt, SortOrder.DESC)
      .limit(1)
      .singleOrNull()
      ?.toRoundRecord()
  }

  /**
   * The oldest [limit] not-yet-[RoundStatus.Finished] rounds for [roomId] (earliest `startsAt`
   * first): what [br.com.colman.palavramento.server.round.RoomScheduler] resumes on restart instead
   * of generating a duplicate current/next pair (dossier phase 3 task: "persist as you go so a
   * restart does not lose accepted words").
   */
  suspend fun findPending(roomId: String, limit: Int): List<RoundRecord> = suspendTransaction(database) {
    RoundsTable.selectAll()
      .where { (RoundsTable.roomId eq roomId) and (RoundsTable.status neq RoundStatus.Finished) }
      .orderBy(RoundsTable.startsAt, SortOrder.ASC)
      .limit(limit)
      .map { it.toRoundRecord() }
  }

  suspend fun loadSolution(roundId: String): List<SolvedWord> = suspendTransaction(database) {
    RoundWordsTable.selectAll().where { RoundWordsTable.roundId eq roundId }.map { row ->
      SolvedWord(
        normalized = row[RoundWordsTable.normalized],
        display = row[RoundWordsTable.word],
        path = PalavramentoJson.decodeFromString(row[RoundWordsTable.pathJson]),
        score = row[RoundWordsTable.score],
        tier = WordTier.valueOf(row[RoundWordsTable.tier]),
      )
    }
  }

  private fun ResultRow.toPlayerCounts(): RoundPlayerCounts? {
    val humans = this[RoundsTable.humanPlayers] ?: return null
    return RoundPlayerCounts(humans, this[RoundsTable.botPlayers] ?: 0)
  }

  private fun ResultRow.toRoundRecord(): RoundRecord {
    val tiles = PalavramentoJson.decodeFromString<List<Tile>>(this[RoundsTable.boardJson])
    val size = sqrt(tiles.size.toDouble()).toInt()
    return RoundRecord(
      id = this[RoundsTable.id],
      roomId = this[RoundsTable.roomId],
      seed = this[RoundsTable.seed],
      board = Board(size, tiles),
      mutator = PalavramentoJson.decodeFromString(this[RoundsTable.mutatorJson]),
      themeTitle = this[RoundsTable.themeTitle],
      themeSubtitle = this[RoundsTable.themeSubtitle],
      commonMin = this[RoundsTable.commonMin],
      maxScore = this[RoundsTable.maxScore],
      maxWords = this[RoundsTable.maxWords],
      startsAt = this[RoundsTable.startsAt].toInstant(),
      endsAt = this[RoundsTable.endsAt].toInstant(),
      status = this[RoundsTable.status],
    )
  }
}

private fun Instant.toOffset(): OffsetDateTime = atOffset(ZoneOffset.UTC)
