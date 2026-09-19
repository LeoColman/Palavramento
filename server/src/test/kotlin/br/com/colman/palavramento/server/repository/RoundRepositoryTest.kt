// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.repository

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.solver.SolvedWord
import br.com.colman.palavramento.domain.solver.WordTier
import br.com.colman.palavramento.server.round.RoundRecord
import br.com.colman.palavramento.server.round.RoundStatus
import br.com.colman.palavramento.server.testsupport.testDatabase
import br.com.colman.palavramento.server.testsupport.testRoomId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

private fun fixtureRecord(
  roomId: String,
  id: String = UUID.randomUUID().toString(),
  startsAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
  endsAt: Instant = startsAt.plusSeconds(60),
  status: String = RoundStatus.Scheduled,
) = RoundRecord(
  id = id,
  roomId = roomId,
  seed = 1L,
  board = Board(4, List(16) { Tile("A", 1) }),
  mutator = Mutator.NoMutator,
  themeTitle = "Grade padrão",
  themeSubtitle = "0 palavras comuns",
  commonMin = 0,
  maxScore = 10,
  maxWords = 1,
  startsAt = startsAt,
  endsAt = endsAt,
  status = status,
)

/**
 * [RoundRepository] against a real database (dossier §7 `rounds`/`round_words`): every field's
 * round trip through the `board_json`/`mutator_json` serialization, and the queries
 * [RoundGenerationService] and [br.com.colman.palavramento.server.round.RoomScheduler] rely on for
 * restart recovery ([RoundRepository.findPending]) and the app's own history screen
 * ([RoundRepository.findLatest]).
 */
class RoundRepositoryTest : FunSpec({
  val database = testDatabase()

  test("insert persists a round whose fields round-trip exactly through findById") {
    val repository = RoundRepository(database)
    val roomId = testRoomId()
    val board = Board(
      4,
      listOf(
        Tile("L", 10), Tile("O", 2), Tile("A", 1), Tile("R", 2),
        Tile("M", 3), Tile("I", 2), Tile("QU", 8), Tile("T", 4),
        Tile("P", 5), Tile("V", 6), Tile("A/F", 20), Tile("I", 2),
        Tile("E", 1), Tile("O", 2), Tile("S", 1), Tile("D", 4),
      ),
    )
    val startsAt = Instant.parse("2026-02-01T10:00:00Z")
    val record = RoundRecord(
      id = UUID.randomUUID().toString(),
      roomId = roomId,
      seed = 777L,
      board = board,
      mutator = Mutator.OneOrOther('A', 'F'),
      themeTitle = "Uma ou outra: A/F",
      themeSubtitle = "15 palavras comuns",
      commonMin = 15,
      maxScore = 340,
      maxWords = 28,
      startsAt = startsAt,
      endsAt = startsAt.plusSeconds(120),
      status = RoundStatus.Scheduled,
    )

    repository.insert(record, emptyList())
    val loaded = repository.findById(record.id)

    loaded.shouldNotBeNull()
    loaded shouldBe record
  }

  test("findById returns null for an unknown round id") {
    val repository = RoundRepository(database)

    repository.findById("does-not-exist-${UUID.randomUUID()}").shouldBeNull()
  }

  test("insert persists the round's solution and loadSolution reads every field back") {
    val repository = RoundRepository(database)
    val record = fixtureRecord(testRoomId())
    val solution = listOf(
      SolvedWord("SOL", "sol", listOf(0, 1, 2), 6, WordTier.Common),
      SolvedWord(
        "ESPECIALISTA",
        "especialista",
        listOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14),
        200,
        WordTier.Expert,
      ),
    )

    repository.insert(record, solution)

    repository.loadSolution(record.id).toSet() shouldBe solution.toSet()
  }

  test("loadSolution returns an empty list when the round was persisted with no solution") {
    val repository = RoundRepository(database)
    val record = fixtureRecord(testRoomId())

    repository.insert(record, emptyList())

    repository.loadSolution(record.id).shouldBeEmpty()
  }

  test("updateStatus overwrites only the status field") {
    val repository = RoundRepository(database)
    val record = fixtureRecord(testRoomId(), status = RoundStatus.Scheduled)
    repository.insert(record, emptyList())

    repository.updateStatus(record.id, RoundStatus.Finished)

    val reloaded = repository.findById(record.id)
    reloaded.shouldNotBeNull()
    reloaded.status shouldBe RoundStatus.Finished
    reloaded.copy(status = RoundStatus.Scheduled) shouldBe record
  }

  test("findLatest returns null for a room with no rounds yet") {
    val repository = RoundRepository(database)

    repository.findLatest(testRoomId()).shouldBeNull()
  }

  test("findLatest picks the round with the latest startsAt, regardless of insertion order") {
    val repository = RoundRepository(database)
    val roomId = testRoomId()
    val base = Instant.parse("2026-03-01T00:00:00Z")
    val middle = fixtureRecord(roomId, startsAt = base.plusSeconds(60), endsAt = base.plusSeconds(120))
    val latest = fixtureRecord(roomId, startsAt = base.plusSeconds(600), endsAt = base.plusSeconds(660))
    val earliest = fixtureRecord(roomId, startsAt = base, endsAt = base.plusSeconds(60))

    // Inserted out of chronological order on purpose: findLatest must sort by startsAt, not rely on insertion order.
    repository.insert(middle, emptyList())
    repository.insert(latest, emptyList())
    repository.insert(earliest, emptyList())

    repository.findLatest(roomId)?.id shouldBe latest.id
  }

  test("findPending returns an empty list for a room with no rounds") {
    val repository = RoundRepository(database)

    repository.findPending(testRoomId(), limit = 5).shouldBeEmpty()
  }

  test("findPending excludes finished rounds and orders the rest by startsAt ascending") {
    val repository = RoundRepository(database)
    val roomId = testRoomId()
    val base = Instant.parse("2026-04-01T00:00:00Z")
    val finished =
      fixtureRecord(roomId, startsAt = base, endsAt = base.plusSeconds(60), status = RoundStatus.Finished)
    val second =
      fixtureRecord(roomId, startsAt = base.plusSeconds(200), endsAt = base.plusSeconds(260))
    val first = fixtureRecord(
      roomId,
      startsAt = base.plusSeconds(100),
      endsAt = base.plusSeconds(160),
      status = RoundStatus.Active,
    )

    repository.insert(finished, emptyList())
    repository.insert(second, emptyList())
    repository.insert(first, emptyList())

    repository.findPending(roomId, limit = 10).map { it.id } shouldContainExactly listOf(first.id, second.id)
  }

  test("findPending respects the limit, keeping only the oldest entries") {
    val repository = RoundRepository(database)
    val roomId = testRoomId()
    val base = Instant.parse("2026-05-01T00:00:00Z")
    val first = fixtureRecord(roomId, startsAt = base, endsAt = base.plusSeconds(60))
    val second = fixtureRecord(roomId, startsAt = base.plusSeconds(100), endsAt = base.plusSeconds(160))
    val third = fixtureRecord(roomId, startsAt = base.plusSeconds(200), endsAt = base.plusSeconds(260))

    repository.insert(third, emptyList())
    repository.insert(first, emptyList())
    repository.insert(second, emptyList())

    repository.findPending(roomId, limit = 1).map { it.id } shouldContainExactly listOf(first.id)
    repository.findPending(roomId, limit = 2).map { it.id } shouldContainExactly listOf(first.id, second.id)
  }

  test("findPending only returns rounds for the requested room") {
    val repository = RoundRepository(database)
    val roomA = testRoomId()
    val roomB = testRoomId()
    val base = Instant.parse("2026-06-01T00:00:00Z")
    val roundA = fixtureRecord(roomA, startsAt = base, endsAt = base.plusSeconds(60))
    val roundB = fixtureRecord(roomB, startsAt = base, endsAt = base.plusSeconds(60))

    repository.insert(roundA, emptyList())
    repository.insert(roundB, emptyList())

    repository.findPending(roomA, limit = 10).map { it.id } shouldContainExactly listOf(roundA.id)
  }
})
