// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.round

import br.com.colman.palavramento.server.repository.PlayerRepository
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
})
