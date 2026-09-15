// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import br.com.colman.palavramento.domain.protocol.PalavramentoJson
import br.com.colman.palavramento.domain.protocol.RoundHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** How many rounds the local cache keeps (dossier 7: "as ultimas 50 rodadas"), newest first. */
const val MaxCachedRounds = 50

/**
 * Local cache of [RoundHistoryEntry] rows (dossier 7, ADR 0009): the History screen (task brief
 * deliverable 3) reads exclusively from here, so it works with the network off. [SyncService] is
 * the only writer, upserting whatever `/players/me/rounds` last returned and trimming down to
 * [MaxCachedRounds] afterwards - the server already caps that response at 50, but trimming here too
 * means a smaller `limit` or a shrinking history server-side is reflected locally as well.
 */
interface HistoryRepository {
  fun rounds(): Flow<List<RoundHistoryEntry>>
  fun round(roundId: String): Flow<RoundHistoryEntry?>
  suspend fun upsertAll(entries: List<RoundHistoryEntry>)
  suspend fun clear()
}

class SqlDelightHistoryRepository(private val database: Database) : HistoryRepository {
  private val queries = database.roundHistoryQueries

  override fun rounds(): Flow<List<RoundHistoryEntry>> =
    queries.selectAll().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toDomain() } }

  override fun round(roundId: String): Flow<RoundHistoryEntry?> =
    queries.selectById(roundId).asFlow().mapToOneOrNull(Dispatchers.IO).map { it?.toDomain() }

  override suspend fun upsertAll(entries: List<RoundHistoryEntry>) = withContext(Dispatchers.IO) {
    queries.transaction {
      entries.forEach { entry ->
        queries.upsert(
          entry.roundId,
          entry.startsAt,
          PalavramentoJson.encodeToString(RoundHistoryEntry.serializer(), entry)
        )
      }
      queries.trimToNewest(MaxCachedRounds.toLong())
    }
  }

  override suspend fun clear() = withContext(Dispatchers.IO) {
    queries.clear()
    Unit
  }
}

private fun RoundHistory.toDomain(): RoundHistoryEntry =
  PalavramentoJson.decodeFromString(RoundHistoryEntry.serializer(), payload)
