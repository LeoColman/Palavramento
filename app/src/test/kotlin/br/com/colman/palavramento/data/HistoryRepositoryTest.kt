// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.data

import android.util.Log
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver.Companion.IN_MEMORY
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.protocol.LabelledWord
import br.com.colman.palavramento.domain.protocol.PalavramentoJson
import br.com.colman.palavramento.domain.protocol.RoundHistoryEntry
import br.com.colman.palavramento.domain.solver.WordTier
import br.com.colman.palavramento.domain.stats.RoundStats
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

private fun sampleBoard(): List<Tile> = List(16) { Tile("A", it % 4 + 1) }

private fun sampleEntry(roundId: String = "round-1", startsAt: Long = 1_000L) = RoundHistoryEntry(
  roundId = roundId,
  startsAt = startsAt,
  board = sampleBoard(),
  mutator = Mutator.ValuableLetter('L', 10),
  themeTitle = "L de alto valor",
  themeSubtitle = "19 palavras comuns",
  maxScore = 4193,
  maxWords = 272,
  stats = RoundStats(
    points = 73,
    words = 6,
    secondsPerWord = 12.2,
    averageLength = 3.5,
    bonusPoints = 0,
    averagePoints = 12.2,
    xp = 14,
  ),
  rank = 3,
  totalPlayers = 12,
  words = listOf(
    LabelledWord("LIMO", 17, WordTier.Common, listOf(0, 1, 2, 3), found = true),
    LabelledWord("PILO", 19, WordTier.Expert, listOf(4, 5, 6, 7), found = false),
  ),
)

/** [SqlDelightHistoryRepository] against the JVM SQLite driver (task brief: "repository tests on the JVM driver"). */
class HistoryRepositoryTest : FunSpec({

  fun newDatabase(): Database {
    val driver = JdbcSqliteDriver(IN_MEMORY)
    Database.Schema.create(driver)
    return Database(driver)
  }

  fun newRepository(): HistoryRepository = SqlDelightHistoryRepository(newDatabase())

  test("rounds is empty before any upsert") {
    runTest {
      newRepository().rounds().first().shouldBeEmpty()
    }
  }

  test("upsertAll then rounds reads back a full RoundHistoryEntry, unchanged") {
    runTest {
      val repository = newRepository()
      val entry = sampleEntry()

      repository.upsertAll(listOf(entry))

      repository.rounds().first() shouldBe listOf(entry)
    }
  }

  test("round(id) reads back the same entry as rounds()") {
    runTest {
      val repository = newRepository()
      val entry = sampleEntry()
      repository.upsertAll(listOf(entry))

      repository.round(entry.roundId).first() shouldBe entry
    }
  }

  test("round(id) is null for an id never upserted") {
    runTest {
      newRepository().round("missing").first() shouldBe null
    }
  }

  test("upsert replaces a round with the same id instead of duplicating it") {
    runTest {
      val repository = newRepository()
      repository.upsertAll(listOf(sampleEntry(startsAt = 1_000L)))

      val updated = sampleEntry(startsAt = 1_000L).copy(rank = 1)
      repository.upsertAll(listOf(updated))

      val rounds = repository.rounds().first()
      rounds shouldBe listOf(updated)
    }
  }

  test("upsertAll trims the cache down to the newest MaxCachedRounds entries") {
    runTest {
      val repository = newRepository()
      val entries = (1..(MaxCachedRounds + 10)).map { sampleEntry(roundId = "round-$it", startsAt = it.toLong()) }

      repository.upsertAll(entries)

      val cached = repository.rounds().first()
      cached shouldHaveSize MaxCachedRounds
      // Newest first (highest startsAt), and exactly the newest MaxCachedRounds survive the trim.
      val expectedNewest = entries.sortedByDescending { it.startsAt }.take(MaxCachedRounds)
      cached shouldContainExactly expectedNewest
    }
  }

  test("rounds is ordered newest first by startsAt") {
    runTest {
      val repository = newRepository()
      val older = sampleEntry(roundId = "older", startsAt = 1_000L)
      val newer = sampleEntry(roundId = "newer", startsAt = 2_000L)
      repository.upsertAll(listOf(older, newer))

      repository.rounds().first() shouldBe listOf(newer, older)
    }
  }

  test("clear wipes every cached round") {
    runTest {
      val repository = newRepository()
      repository.upsertAll(listOf(sampleEntry()))

      repository.clear()

      repository.rounds().first().shouldBeEmpty()
    }
  }

  // android.util.Log is unmocked on the plain JVM (no Robolectric in this project, see
  // LobbyViewModelTest): the repository logs a warning when it skips an undecodable cached row, so a
  // bare Log.w call would throw "not mocked" before that decode-failure path could be exercised.
  test("A cached round that no longer decodes (ADR 0012: a removed mutator type) is skipped, not crashed on") {
    mockkStatic(Log::class)
    every { Log.w(any(), any<String>()) } returns 0
    try {
      runTest {
        val database = newDatabase()
        val repository = SqlDelightHistoryRepository(database)
        val goodEntry = sampleEntry(roundId = "good", startsAt = 2_000L)
        repository.upsertAll(listOf(goodEntry))

        // A payload the server could have written before ADR 0012 removed TAMANHO_MINIMO/LETRA_PROIBIDA:
        // built by encoding a valid entry and swapping in the legacy discriminator, so this test does
        // not hand-maintain a full RoundHistoryEntry JSON literal.
        val legacyJson = PalavramentoJson.encodeToString(
          RoundHistoryEntry.serializer(),
          sampleEntry(roundId = "legacy", startsAt = 1_000L).copy(mutator = Mutator.NoMutator),
        ).replace(""""type":"SEM_MUTADOR"""", """"type":"TAMANHO_MINIMO","length":5""")
        database.roundHistoryQueries.upsert("legacy", 1_000L, legacyJson)

        repository.rounds().first() shouldBe listOf(goodEntry)
        repository.round("legacy").first() shouldBe null
        repository.round("good").first() shouldBe goodEntry
      }
    } finally {
      unmockkStatic(Log::class)
    }
  }

  isolationMode = IsolationMode.InstancePerTest
})
