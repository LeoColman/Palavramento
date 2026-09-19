// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.repository

import br.com.colman.palavramento.server.round.AcceptedSubmission
import br.com.colman.palavramento.server.round.FoundWord
import br.com.colman.palavramento.server.testsupport.insertFakeFinishedRound
import br.com.colman.palavramento.server.testsupport.insertGuest
import br.com.colman.palavramento.server.testsupport.testDatabase
import br.com.colman.palavramento.server.testsupport.testRoomId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.time.Instant

/**
 * [SubmissionRepository] against a real database (dossier §7 `submissions`), plus the two migration
 * helpers ([SubmissionRepository.reassignRound], [SubmissionRepository.deleteRound])
 * [br.com.colman.palavramento.server.auth.AuthService] uses for a guest-to-registered-account login
 * merge (ADR 0007).
 */
class SubmissionRepositoryTest : FunSpec({
  val database = testDatabase()

  test("insert then findByRoundAndPlayer reads back every field of the submission") {
    val submissionRepository = SubmissionRepository(database)
    val roomId = testRoomId()
    val roundId = RoundRepository(database).insertFakeFinishedRound(roomId)
    val player = PlayerRepository(database).insertGuest()
    val acceptedAt = Instant.parse("2026-01-01T00:00:05Z")

    submissionRepository.insert(AcceptedSubmission(roundId, player.id, "SOL", "sol", 6, listOf(0, 1, 2), acceptedAt))

    submissionRepository.findByRoundAndPlayer(roundId, player.id) shouldContainExactly
      listOf(FoundWord("SOL", "sol", 6, listOf(0, 1, 2), acceptedAt))
  }

  test("findByRoundAndPlayer orders words oldest first, regardless of insertion order") {
    val submissionRepository = SubmissionRepository(database)
    val roomId = testRoomId()
    val roundId = RoundRepository(database).insertFakeFinishedRound(roomId)
    val player = PlayerRepository(database).insertGuest()
    val base = Instant.parse("2026-01-01T00:00:00Z")

    submissionRepository.insert(
      AcceptedSubmission(roundId, player.id, "MIL", "mil", 15, listOf(4, 5, 0), base.plusSeconds(10)),
    )
    submissionRepository.insert(
      AcceptedSubmission(roundId, player.id, "SOL", "sol", 6, listOf(0, 1, 2), base.plusSeconds(1)),
    )

    submissionRepository.findByRoundAndPlayer(roundId, player.id).map { it.normalized } shouldContainExactly
      listOf("SOL", "MIL")
  }

  test("findByRoundAndPlayer returns an empty list for a player who found nothing in that round") {
    val submissionRepository = SubmissionRepository(database)
    val roomId = testRoomId()
    val roundId = RoundRepository(database).insertFakeFinishedRound(roomId)
    val playerRepository = PlayerRepository(database)
    val withWord = playerRepository.insertGuest()
    val withoutWord = playerRepository.insertGuest()
    submissionRepository.insert(
      AcceptedSubmission(roundId, withWord.id, "SOL", "sol", 6, listOf(0, 1, 2), Instant.EPOCH),
    )

    submissionRepository.findByRoundAndPlayer(roundId, withoutWord.id).shouldBeEmpty()
  }

  test("findByRound groups every player's words, each ordered oldest first") {
    val submissionRepository = SubmissionRepository(database)
    val roomId = testRoomId()
    val roundId = RoundRepository(database).insertFakeFinishedRound(roomId)
    val playerRepository = PlayerRepository(database)
    val first = playerRepository.insertGuest()
    val second = playerRepository.insertGuest()
    val base = Instant.parse("2026-01-01T00:00:00Z")

    submissionRepository.insert(
      AcceptedSubmission(roundId, first.id, "MIL", "mil", 15, listOf(4, 5, 0), base.plusSeconds(5)),
    )
    submissionRepository.insert(
      AcceptedSubmission(roundId, first.id, "SOL", "sol", 6, listOf(0, 1, 2), base),
    )
    submissionRepository.insert(
      AcceptedSubmission(roundId, second.id, "REI", "rei", 8, listOf(1, 2, 3), base),
    )

    val grouped = submissionRepository.findByRound(roundId)

    grouped.keys shouldBe setOf(first.id, second.id)
    grouped.getValue(first.id).map { it.normalized } shouldContainExactly listOf("SOL", "MIL")
    grouped.getValue(second.id).map { it.normalized } shouldContainExactly listOf("REI")
  }

  test("findByRound returns an empty map for a round with no submissions") {
    val submissionRepository = SubmissionRepository(database)
    val roomId = testRoomId()
    val roundId = RoundRepository(database).insertFakeFinishedRound(roomId)

    submissionRepository.findByRound(roundId).shouldBeEmpty()
  }

  test("reassignRound moves one player's submissions for that round, leaving other rounds and players untouched") {
    val submissionRepository = SubmissionRepository(database)
    val roomId = testRoomId()
    val roundId = RoundRepository(database).insertFakeFinishedRound(roomId)
    val otherRoundId = RoundRepository(database).insertFakeFinishedRound(roomId)
    val playerRepository = PlayerRepository(database)
    val guest = playerRepository.insertGuest()
    val target = playerRepository.insertGuest()
    submissionRepository.insert(AcceptedSubmission(roundId, guest.id, "SOL", "sol", 6, listOf(0, 1, 2), Instant.EPOCH))
    submissionRepository.insert(
      AcceptedSubmission(otherRoundId, guest.id, "MIL", "mil", 15, listOf(4, 5, 0), Instant.EPOCH),
    )

    suspendTransaction(database) { submissionRepository.reassignRound(this, roundId, guest.id, target.id) }

    submissionRepository.findByRoundAndPlayer(roundId, guest.id).shouldBeEmpty()
    submissionRepository.findByRoundAndPlayer(roundId, target.id).map { it.normalized } shouldContainExactly
      listOf("SOL")
    submissionRepository.findByRoundAndPlayer(otherRoundId, guest.id).map { it.normalized } shouldContainExactly
      listOf("MIL")
  }

  test("deleteRound removes only that player's submissions for that round") {
    val submissionRepository = SubmissionRepository(database)
    val roomId = testRoomId()
    val roundId = RoundRepository(database).insertFakeFinishedRound(roomId)
    val playerRepository = PlayerRepository(database)
    val guest = playerRepository.insertGuest()
    val other = playerRepository.insertGuest()
    submissionRepository.insert(AcceptedSubmission(roundId, guest.id, "SOL", "sol", 6, listOf(0, 1, 2), Instant.EPOCH))
    submissionRepository.insert(AcceptedSubmission(roundId, other.id, "REI", "rei", 8, listOf(1, 2, 3), Instant.EPOCH))

    suspendTransaction(database) { submissionRepository.deleteRound(this, roundId, guest.id) }

    submissionRepository.findByRoundAndPlayer(roundId, guest.id).shouldBeEmpty()
    submissionRepository.findByRoundAndPlayer(roundId, other.id).map { it.normalized } shouldContainExactly
      listOf("REI")
  }
})
