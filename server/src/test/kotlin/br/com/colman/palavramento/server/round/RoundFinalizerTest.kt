// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.round

import br.com.colman.palavramento.server.repository.PasswordAuthProvider
import br.com.colman.palavramento.server.repository.PlayerRepository
import br.com.colman.palavramento.server.repository.PlayerRow
import br.com.colman.palavramento.server.repository.PlayerStatsRepository
import br.com.colman.palavramento.server.repository.RoundRepository
import br.com.colman.palavramento.server.repository.RoundResultRepository
import br.com.colman.palavramento.server.testsupport.insertFakeFinishedRound
import br.com.colman.palavramento.server.testsupport.insertGuest
import br.com.colman.palavramento.server.testsupport.testDatabase
import br.com.colman.palavramento.server.testsupport.testRoomId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

/**
 * [RoundFinalizer.finalize] with fixed [Instant]s standing in for a fake clock (dossier phase 3
 * task style, ADR 0010): a late joiner's `secondsPerWord` must come from their own entry time,
 * never the round's own start.
 */
class RoundFinalizerTest : FunSpec({
  val database = testDatabase()

  test("a late joiner's secondsPerWord is measured from their own entry time, not the round start") {
    val roomId = testRoomId()
    val playerRepository = PlayerRepository(database)
    val roundRepository = RoundRepository(database)
    val roundResultRepository = RoundResultRepository(database)
    val playerStatsRepository = PlayerStatsRepository(database)
    val finalizer = RoundFinalizer(playerRepository, roundResultRepository, playerStatsRepository)

    val onTimePlayer = playerRepository.insertGuest()
    val lateJoiner = playerRepository.insertGuest()
    val roundId = roundRepository.insertFakeFinishedRound(roomId)

    val roundStartsAt = Instant.parse("2026-01-01T00:00:00Z")
    // Joined 90s into a 120s round: their own clock starts there, not at roundStartsAt.
    val lateEntry = roundStartsAt.plusSeconds(90)

    val onTimeWord = FoundWord("casa", "casa", 10, listOf(0, 1, 2, 3), roundStartsAt.plusSeconds(40))
    val lateWord = FoundWord("sol", "sol", 6, listOf(4, 5, 6), lateEntry.plusSeconds(5))

    val result = finalizer.finalize(
      roundId = roundId,
      perPlayerFound = mapOf(onTimePlayer.id to listOf(onTimeWord), lateJoiner.id to listOf(lateWord)),
      perPlayerEnteredAt = mapOf(onTimePlayer.id to roundStartsAt, lateJoiner.id to lateEntry),
    )

    val onTimeOutcome = result.outcomes.single { it.playerId == onTimePlayer.id }
    val lateOutcome = result.outcomes.single { it.playerId == lateJoiner.id }

    // On-time player: found at +40s from roundStartsAt, one word -> 40.0s/word.
    onTimeOutcome.stats.secondsPerWord shouldBe 40.0
    // Late joiner: found 5s after their own entry, not 95s after roundStartsAt.
    lateOutcome.stats.secondsPerWord shouldBe 5.0

    // Persisted enteredAt matches, so a later on-demand recomputation (/players/me/rounds) agrees too.
    val persisted = roundResultRepository.findByRound(roundId).associateBy { it.playerId }
    persisted.getValue(onTimePlayer.id).enteredAt shouldBe roundStartsAt
    persisted.getValue(lateJoiner.id).enteredAt shouldBe lateEntry
  }

  test("a player's leaderboard score is the sum of their found words' scores, not a subtraction") {
    val roomId = testRoomId()
    val playerRepository = PlayerRepository(database)
    val roundRepository = RoundRepository(database)
    val roundResultRepository = RoundResultRepository(database)
    val playerStatsRepository = PlayerStatsRepository(database)
    val finalizer = RoundFinalizer(playerRepository, roundResultRepository, playerStatsRepository)

    val multiWordPlayer = playerRepository.insertGuest()
    val singleWordPlayer = playerRepository.insertGuest()
    val roundId = roundRepository.insertFakeFinishedRound(roomId)
    val roundStartsAt = Instant.parse("2026-01-01T00:00:00Z")

    // 5 + 3 = 8, which must outrank the single word worth 6: a real sum, not "5 - 3 = 2" (or
    // "3 - 5 = -2", depending on traversal order) sliding it below.
    val wordA = FoundWord("casa", "casa", 5, listOf(0, 1, 2, 3), roundStartsAt.plusSeconds(10))
    val wordB = FoundWord("bola", "bola", 3, listOf(4, 5, 6), roundStartsAt.plusSeconds(20))
    val singleWord = FoundWord("sol", "sol", 6, listOf(7, 8, 9), roundStartsAt.plusSeconds(15))

    val result = finalizer.finalize(
      roundId = roundId,
      perPlayerFound = mapOf(
        multiWordPlayer.id to listOf(wordA, wordB),
        singleWordPlayer.id to listOf(singleWord),
      ),
      perPlayerEnteredAt = mapOf(multiWordPlayer.id to roundStartsAt, singleWordPlayer.id to roundStartsAt),
    )

    val multiOutcome = result.outcomes.single { it.playerId == multiWordPlayer.id }
    val singleOutcome = result.outcomes.single { it.playerId == singleWordPlayer.id }

    multiOutcome.stats.points shouldBe 8
    multiOutcome.rank shouldBe 1
    singleOutcome.rank shouldBe 2
  }

  test("finalize applies player_stats to registered players, and to registered players only") {
    val roomId = testRoomId()
    val playerRepository = PlayerRepository(database)
    val roundRepository = RoundRepository(database)
    val roundResultRepository = RoundResultRepository(database)
    val playerStatsRepository = PlayerStatsRepository(database)
    val finalizer = RoundFinalizer(playerRepository, roundResultRepository, playerStatsRepository)

    val guest = playerRepository.insertGuest()
    val registered = PlayerRow(
      id = UUID.randomUUID().toString(),
      displayName = "Registered",
      isGuest = false,
      authProvider = PasswordAuthProvider,
      email = "finalizer-${UUID.randomUUID()}@example.com",
      passwordHash = "irrelevant-hash",
      createdAt = Instant.now(),
    )
    playerRepository.insert(registered)
    val roundId = roundRepository.insertFakeFinishedRound(roomId)
    val roundStartsAt = Instant.parse("2026-01-01T00:00:00Z")
    val word = FoundWord("casa", "casa", 10, listOf(0, 1, 2, 3), roundStartsAt.plusSeconds(5))

    finalizer.finalize(
      roundId = roundId,
      perPlayerFound = mapOf(guest.id to listOf(word), registered.id to listOf(word)),
      perPlayerEnteredAt = mapOf(guest.id to roundStartsAt, registered.id to roundStartsAt),
    )

    playerStatsRepository.get(guest.id) shouldBe null
    playerStatsRepository.get(registered.id)?.gamesPlayed shouldBe 1
    playerStatsRepository.get(registered.id)?.totalScore shouldBe 10L
  }

  test("finalize records no best word (score 0) for a participant who found nothing, unlike one who did") {
    val roomId = testRoomId()
    val playerRepository = PlayerRepository(database)
    val roundRepository = RoundRepository(database)
    val roundResultRepository = RoundResultRepository(database)
    val playerStatsRepository = PlayerStatsRepository(database)
    val finalizer = RoundFinalizer(playerRepository, roundResultRepository, playerStatsRepository)

    val emptyHandedRegistered = PlayerRow(
      id = UUID.randomUUID().toString(),
      displayName = "EmptyHanded",
      isGuest = false,
      authProvider = PasswordAuthProvider,
      email = "emptyhanded-${UUID.randomUUID()}@example.com",
      passwordHash = "irrelevant-hash",
      createdAt = Instant.now(),
    )
    val scoringRegistered = PlayerRow(
      id = UUID.randomUUID().toString(),
      displayName = "Scoring",
      isGuest = false,
      authProvider = PasswordAuthProvider,
      email = "scoring-${UUID.randomUUID()}@example.com",
      passwordHash = "irrelevant-hash",
      createdAt = Instant.now(),
    )
    playerRepository.insert(emptyHandedRegistered)
    playerRepository.insert(scoringRegistered)
    val roundId = roundRepository.insertFakeFinishedRound(roomId)
    val roundStartsAt = Instant.parse("2026-01-01T00:00:00Z")
    val word = FoundWord("casa", "casa", 9, listOf(0, 1, 2, 3), roundStartsAt.plusSeconds(5))

    finalizer.finalize(
      roundId = roundId,
      perPlayerFound = mapOf(emptyHandedRegistered.id to emptyList(), scoringRegistered.id to listOf(word)),
      perPlayerEnteredAt = mapOf(emptyHandedRegistered.id to roundStartsAt, scoringRegistered.id to roundStartsAt),
    )

    val emptyHandedStats = playerStatsRepository.get(emptyHandedRegistered.id)
    emptyHandedStats?.bestWord shouldBe null
    emptyHandedStats?.bestWordScore shouldBe 0

    val scoringStats = playerStatsRepository.get(scoringRegistered.id)
    scoringStats?.bestWord shouldBe "casa"
    scoringStats?.bestWordScore shouldBe 9
  }

  test("a player who deleted their account mid-round is dropped instead of breaking the whole round") {
    val roomId = testRoomId()
    val playerRepository = PlayerRepository(database)
    val roundRepository = RoundRepository(database)
    val roundResultRepository = RoundResultRepository(database)
    val playerStatsRepository = PlayerStatsRepository(database)
    val finalizer = RoundFinalizer(playerRepository, roundResultRepository, playerStatsRepository)

    val stayed = playerRepository.insertGuest()
    val deleted = playerRepository.insertGuest()
    val roundId = roundRepository.insertFakeFinishedRound(roomId)
    val roundStartsAt = Instant.parse("2026-01-01T00:00:00Z")
    val stayedWord = FoundWord("casa", "casa", 10, listOf(0, 1, 2, 3), roundStartsAt.plusSeconds(10))
    val deletedWord = FoundWord("sol", "sol", 99, listOf(4, 5, 6), roundStartsAt.plusSeconds(20))

    // What happened in production on 2026-09-25: they played the round, then called
    // DELETE /players/me before it ended, so the row round_results points at is already gone.
    playerRepository.delete(deleted.id)

    val result = finalizer.finalize(
      roundId = roundId,
      perPlayerFound = mapOf(stayed.id to listOf(stayedWord), deleted.id to listOf(deletedWord)),
      perPlayerEnteredAt = mapOf(stayed.id to roundStartsAt, deleted.id to roundStartsAt),
    )

    // The round finishes for everyone else, and the deleted account is in nobody's leaderboard,
    // not even as a nameless entry holding first place with its 99 points.
    result.outcomes.map { it.playerId } shouldBe listOf(stayed.id)
    result.totalPlayers shouldBe 1
    result.outcomes.single().rank shouldBe 1
    roundResultRepository.findByRound(roundId).map { it.playerId } shouldBe listOf(stayed.id)
  }

  test("a round whose only player deleted their account finishes with nothing to persist") {
    val roomId = testRoomId()
    val playerRepository = PlayerRepository(database)
    val roundRepository = RoundRepository(database)
    val roundResultRepository = RoundResultRepository(database)
    val playerStatsRepository = PlayerStatsRepository(database)
    val finalizer = RoundFinalizer(playerRepository, roundResultRepository, playerStatsRepository)

    val deleted = playerRepository.insertGuest()
    val roundId = roundRepository.insertFakeFinishedRound(roomId)
    val roundStartsAt = Instant.parse("2026-01-01T00:00:00Z")
    val word = FoundWord("casa", "casa", 10, listOf(0, 1, 2, 3), roundStartsAt.plusSeconds(10))
    playerRepository.delete(deleted.id)

    val result = finalizer.finalize(
      roundId = roundId,
      perPlayerFound = mapOf(deleted.id to listOf(word)),
      perPlayerEnteredAt = mapOf(deleted.id to roundStartsAt),
    )

    result.outcomes shouldBe emptyList()
    result.totalPlayers shouldBe 0
    roundResultRepository.findByRound(roundId) shouldBe emptyList()
  }
})
