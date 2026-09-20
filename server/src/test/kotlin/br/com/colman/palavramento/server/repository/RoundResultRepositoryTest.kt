// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.repository

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.server.round.RoundRecord
import br.com.colman.palavramento.server.round.RoundStatus
import br.com.colman.palavramento.server.testsupport.insertFakeFinishedRound
import br.com.colman.palavramento.server.testsupport.insertGuest
import br.com.colman.palavramento.server.testsupport.testDatabase
import br.com.colman.palavramento.server.testsupport.testRoomId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.time.Instant
import java.util.UUID

/** An unpersisted round with a caller-chosen id, for tests that need round ids sorted deterministically. */
private fun fixtureRound(id: String, roomId: String) = RoundRecord(
  id = id,
  roomId = roomId,
  seed = 0L,
  board = Board(4, List(16) { Tile("A", 1) }),
  mutator = Mutator.NoMutator,
  themeTitle = "Grade padrão",
  themeSubtitle = "0 palavras comuns",
  commonMin = 0,
  maxScore = 0,
  maxWords = 0,
  startsAt = Instant.EPOCH,
  endsAt = Instant.EPOCH.plusSeconds(60),
  status = RoundStatus.Finished,
)

private fun resultRow(roundId: String, playerId: String, score: Int = 10, rank: Int = 1) = RoundResultRow(
  roundId = roundId,
  playerId = playerId,
  score = score,
  words = 1,
  rank = rank,
  xp = 5,
  enteredAt = Instant.parse("2026-01-01T00:00:00Z"),
)

/**
 * [RoundResultRepository] against a real database (dossier §7 `round_results`), plus the two
 * migration helpers ([RoundResultRepository.reassignRound], [RoundResultRepository.deleteRound])
 * [br.com.colman.palavramento.server.auth.AuthService] uses for a guest-to-registered-account login
 * merge (ADR 0007).
 */
class RoundResultRepositoryTest : FunSpec({
  val database = testDatabase()

  test("insert then findByRound reads back every field of the row") {
    val roundResultRepository = RoundResultRepository(database)
    val roomId = testRoomId()
    val roundId = RoundRepository(database).insertFakeFinishedRound(roomId)
    val player = PlayerRepository(database).insertGuest()
    val enteredAt = Instant.parse("2026-01-01T00:00:10Z")
    val row = RoundResultRow(
      roundId = roundId,
      playerId = player.id,
      score = 120,
      words = 7,
      rank = 1,
      xp = 30,
      enteredAt = enteredAt,
      doubleXp = true,
    )

    suspendTransaction(database) { roundResultRepository.insert(this, row) }

    roundResultRepository.findByRound(roundId) shouldContainExactly listOf(row)
  }

  test("findByRound returns an empty list for a round with no results") {
    val roundResultRepository = RoundResultRepository(database)
    val roomId = testRoomId()
    val roundId = RoundRepository(database).insertFakeFinishedRound(roomId)

    roundResultRepository.findByRound(roundId).shouldBeEmpty()
  }

  test("doubleXp defaults to false when not set explicitly") {
    val roundResultRepository = RoundResultRepository(database)
    val roomId = testRoomId()
    val roundId = RoundRepository(database).insertFakeFinishedRound(roomId)
    val player = PlayerRepository(database).insertGuest()

    suspendTransaction(database) { roundResultRepository.insert(this, resultRow(roundId, player.id)) }

    roundResultRepository.findByRound(roundId).single().doubleXp shouldBe false
  }

  test("countPlayers counts exactly the rows persisted for that round, zero when none") {
    val roundResultRepository = RoundResultRepository(database)
    val roomId = testRoomId()
    val roundId = RoundRepository(database).insertFakeFinishedRound(roomId)

    roundResultRepository.countPlayers(roundId) shouldBe 0

    val playerRepository = PlayerRepository(database)
    val first = playerRepository.insertGuest()
    val second = playerRepository.insertGuest()
    suspendTransaction(database) {
      roundResultRepository.insert(this, resultRow(roundId, first.id, rank = 1))
      roundResultRepository.insert(this, resultRow(roundId, second.id, rank = 2))
    }

    roundResultRepository.countPlayers(roundId) shouldBe 2
  }

  test("findByPlayer orders by roundId descending and honors the limit") {
    val roundResultRepository = RoundResultRepository(database)
    val roundRepository = RoundRepository(database)
    val roomId = testRoomId()
    val player = PlayerRepository(database).insertGuest()
    // Sortable and unique: findByPlayer orders by round id, and PIT re-runs this test against the
    // same database, where a fixed id would collide with the row the previous run left behind.
    val run = UUID.randomUUID()
    roundRepository.insert(fixtureRound("a-$run", roomId), emptyList())
    roundRepository.insert(fixtureRound("b-$run", roomId), emptyList())
    roundRepository.insert(fixtureRound("c-$run", roomId), emptyList())
    suspendTransaction(database) {
      roundResultRepository.insert(this, resultRow("a-$run", player.id))
      roundResultRepository.insert(this, resultRow("b-$run", player.id))
      roundResultRepository.insert(this, resultRow("c-$run", player.id))
    }

    val expectedDesc = listOf("c-$run", "b-$run", "a-$run")
    roundResultRepository.findByPlayer(player.id, limit = 10).map { it.roundId } shouldContainExactly expectedDesc
    roundResultRepository.findByPlayer(player.id, limit = 2).map { it.roundId } shouldContainExactly
      expectedDesc.take(2)
  }

  test("findByPlayer returns an empty list for a player with no results") {
    val roundResultRepository = RoundResultRepository(database)

    roundResultRepository.findByPlayer(PlayerRepository(database).insertGuest().id, limit = 10).shouldBeEmpty()
  }

  test("findByPlayerAll ignores the per-round limit and returns every result across rounds") {
    val roundResultRepository = RoundResultRepository(database)
    val roundRepository = RoundRepository(database)
    val roomId = testRoomId()
    val player = PlayerRepository(database).insertGuest()
    val rounds = List(3) { roundRepository.insertFakeFinishedRound(roomId) }
    suspendTransaction(database) {
      rounds.forEach { roundResultRepository.insert(this, resultRow(it, player.id)) }
    }

    roundResultRepository.findByPlayerAll(player.id).map { it.roundId }.toSet() shouldBe rounds.toSet()
  }

  test("reassignRound moves one player's result to another, leaving other rounds and players untouched") {
    val roundResultRepository = RoundResultRepository(database)
    val roundRepository = RoundRepository(database)
    val playerRepository = PlayerRepository(database)
    val roomId = testRoomId()
    val guest = playerRepository.insertGuest()
    val target = playerRepository.insertGuest()
    val movedRound = roundRepository.insertFakeFinishedRound(roomId)
    val untouchedRound = roundRepository.insertFakeFinishedRound(roomId)
    suspendTransaction(database) {
      roundResultRepository.insert(this, resultRow(movedRound, guest.id, score = 42))
      roundResultRepository.insert(this, resultRow(untouchedRound, guest.id, score = 9))
    }

    suspendTransaction(database) { roundResultRepository.reassignRound(this, movedRound, guest.id, target.id) }

    roundResultRepository.findByRound(movedRound).map { it.playerId } shouldContainExactly listOf(target.id)
    roundResultRepository.findByRound(movedRound).single().score shouldBe 42
    roundResultRepository.findByRound(untouchedRound).single().playerId shouldBe guest.id
  }

  test("deleteRound removes only that player's row for that round") {
    val roundResultRepository = RoundResultRepository(database)
    val roundRepository = RoundRepository(database)
    val playerRepository = PlayerRepository(database)
    val roomId = testRoomId()
    val guest = playerRepository.insertGuest()
    val other = playerRepository.insertGuest()
    val roundId = roundRepository.insertFakeFinishedRound(roomId)
    suspendTransaction(database) {
      roundResultRepository.insert(this, resultRow(roundId, guest.id))
      roundResultRepository.insert(this, resultRow(roundId, other.id))
    }

    suspendTransaction(database) { roundResultRepository.deleteRound(this, roundId, guest.id) }

    roundResultRepository.findByRound(roundId).map { it.playerId } shouldContainExactly listOf(other.id)
  }
})
