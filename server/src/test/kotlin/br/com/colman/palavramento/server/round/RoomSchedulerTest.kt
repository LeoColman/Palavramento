// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.round

import br.com.colman.palavramento.domain.protocol.ServerMessage
import br.com.colman.palavramento.domain.submission.RejectionReason
import br.com.colman.palavramento.server.repository.PlayerRepository
import br.com.colman.palavramento.server.repository.RoundRepository
import br.com.colman.palavramento.server.testsupport.TestLexicon
import br.com.colman.palavramento.server.testsupport.buildTestScheduler
import br.com.colman.palavramento.server.testsupport.insertGuest
import br.com.colman.palavramento.server.testsupport.testDatabase
import br.com.colman.palavramento.server.testsupport.testRoomId
import br.com.colman.palavramento.server.testsupport.testServerConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Exercises [RoomScheduler]'s submission-window and join logic directly, against a real database
 * and the real lexicon, but without the real-time loop (dossier phase 3: "scheduler driven by an
 * injectable clock"). Driving these edge cases (late-tolerance boundary, rate limiting) through a
 * live WebSocket connection would race the real clock against network/test scheduling latency;
 * [RoomScheduler.activateForTesting] sidesteps that by activating a round directly and letting a
 * [MutableGameClock] set exactly the instant each assertion needs.
 */
class RoomSchedulerTest : FunSpec({
  val database = testDatabase()

  test("a submission inside the round window validates against the real lexicon") {
    val clock = MutableGameClock(Instant.parse("2026-01-01T00:00:00Z"))
    val config = testServerConfig(roundDuration = 1.minutes, intermissionDuration = 1.minutes)
    val roomId = testRoomId()
    val scheduler = buildTestScheduler(database, clock, config, roomId)
    val player = PlayerRepository(database).insertGuest()

    val generated = RoundGenerationService(TestLexicon.lexicon, config, RoundRepository(database))
      .generateAndPersist(roomId, clock.now(), RoundTiming.endsAt(clock.now(), config.roundDuration))
    val state = scheduler.activateForTesting(generated)
    state.markParticipant(player.id, generated.record.startsAt)

    val word = generated.solution.first()
    val response = scheduler.submitWord(player.id, generated.record.id, word.path)

    response.shouldBeInstanceOf<ServerMessage.WordAccepted>()
    val accepted = response as ServerMessage.WordAccepted
    accepted.word shouldBe word.display
    accepted.score shouldBe word.score
    accepted.runningScore shouldBe word.score
    accepted.runningWords shouldBe 1
  }

  test("a submission before the round starts is silently discarded") {
    val clock = MutableGameClock(Instant.parse("2026-01-01T00:00:00Z"))
    val config = testServerConfig(roundDuration = 1.minutes, intermissionDuration = 1.minutes)
    val roomId = testRoomId()
    val scheduler = buildTestScheduler(database, clock, config, roomId)
    val player = PlayerRepository(database).insertGuest()

    val startsAt = clock.now().plusSeconds(30)
    val generated = RoundGenerationService(TestLexicon.lexicon, config, RoundRepository(database))
      .generateAndPersist(roomId, startsAt, RoundTiming.endsAt(startsAt, config.roundDuration))
    val state = scheduler.activateForTesting(generated)
    state.markParticipant(player.id, generated.record.startsAt)

    // clock is still before startsAt
    val response = scheduler.submitWord(player.id, generated.record.id, generated.solution.first().path)

    response shouldBe null
  }

  test("a submission after the late-tolerance window is silently discarded") {
    val clock = MutableGameClock(Instant.parse("2026-01-01T00:00:00Z"))
    val config = testServerConfig(roundDuration = 1.minutes, intermissionDuration = 1.minutes)
    val roomId = testRoomId()
    val scheduler = buildTestScheduler(database, clock, config, roomId)
    val player = PlayerRepository(database).insertGuest()

    val startsAt = clock.now()
    val endsAt = RoundTiming.endsAt(startsAt, config.roundDuration)
    val generated = RoundGenerationService(TestLexicon.lexicon, config, RoundRepository(database))
      .generateAndPersist(roomId, startsAt, endsAt)
    val state = scheduler.activateForTesting(generated)
    state.markParticipant(player.id, generated.record.startsAt)

    clock.set(RoundTiming.lateSubmissionDeadline(endsAt, config.lateSubmissionTolerance).plusMillis(1))
    val response = scheduler.submitWord(player.id, generated.record.id, generated.solution.first().path)

    response shouldBe null
  }

  test("a submission still inside the late-tolerance window is validated, not discarded") {
    val clock = MutableGameClock(Instant.parse("2026-01-01T00:00:00Z"))
    val config = testServerConfig(roundDuration = 1.minutes, intermissionDuration = 1.minutes)
    val roomId = testRoomId()
    val scheduler = buildTestScheduler(database, clock, config, roomId)
    val player = PlayerRepository(database).insertGuest()

    val startsAt = clock.now()
    val endsAt = RoundTiming.endsAt(startsAt, config.roundDuration)
    val generated = RoundGenerationService(TestLexicon.lexicon, config, RoundRepository(database))
      .generateAndPersist(roomId, startsAt, endsAt)
    val state = scheduler.activateForTesting(generated)
    state.markParticipant(player.id, generated.record.startsAt)

    clock.set(endsAt.plusMillis(config.lateSubmissionTolerance.inWholeMilliseconds - 1))
    val response = scheduler.submitWord(player.id, generated.record.id, generated.solution.first().path)

    response.shouldBeInstanceOf<ServerMessage.WordAccepted>()
  }

  test("reconnecting participant gets RoundStart with alreadyFound restored") {
    val clock = MutableGameClock(Instant.parse("2026-01-01T00:00:00Z"))
    val config = testServerConfig(roundDuration = 1.minutes, intermissionDuration = 1.minutes)
    val roomId = testRoomId()
    val scheduler = buildTestScheduler(database, clock, config, roomId)
    val player = PlayerRepository(database).insertGuest()

    val generated = RoundGenerationService(TestLexicon.lexicon, config, RoundRepository(database))
      .generateAndPersist(roomId, clock.now(), RoundTiming.endsAt(clock.now(), config.roundDuration))
    val state = scheduler.activateForTesting(generated)
    state.markParticipant(player.id, generated.record.startsAt)

    val word = generated.solution.first()
    scheduler.submitWord(player.id, generated.record.id, word.path)

    val result = scheduler.join(player.id)

    result.shouldBeInstanceOf<JoinResult.Started>()
    val started = result as JoinResult.Started
    started.message.roundId shouldBe generated.record.id
    started.message.alreadyFound.map { it.word } shouldBe listOf(word.display)
    started.message.runningScore shouldBe word.score
    started.message.runningWords shouldBe 1
  }

  test("duplicate submission of the same word is rejected as JA_ENCONTRADA") {
    val clock = MutableGameClock(Instant.parse("2026-01-01T00:00:00Z"))
    val config = testServerConfig(roundDuration = 1.minutes, intermissionDuration = 1.minutes)
    val roomId = testRoomId()
    val scheduler = buildTestScheduler(database, clock, config, roomId)
    val player = PlayerRepository(database).insertGuest()

    val generated = RoundGenerationService(TestLexicon.lexicon, config, RoundRepository(database))
      .generateAndPersist(roomId, clock.now(), RoundTiming.endsAt(clock.now(), config.roundDuration))
    val state = scheduler.activateForTesting(generated)
    state.markParticipant(player.id, generated.record.startsAt)

    val word = generated.solution.first()
    scheduler.submitWord(player.id, generated.record.id, word.path)
    val secondAttempt = scheduler.submitWord(player.id, generated.record.id, word.path)

    secondAttempt.shouldBeInstanceOf<ServerMessage.WordRejected>()
    (secondAttempt as ServerMessage.WordRejected).reason shouldBe RejectionReason.AlreadyFound
  }

  test("a non-adjacent path is rejected as CAMINHO_INVALIDO") {
    val clock = MutableGameClock(Instant.parse("2026-01-01T00:00:00Z"))
    val config = testServerConfig(roundDuration = 1.minutes, intermissionDuration = 1.minutes)
    val roomId = testRoomId()
    val scheduler = buildTestScheduler(database, clock, config, roomId)
    val player = PlayerRepository(database).insertGuest()

    val generated = RoundGenerationService(TestLexicon.lexicon, config, RoundRepository(database))
      .generateAndPersist(roomId, clock.now(), RoundTiming.endsAt(clock.now(), config.roundDuration))
    val state = scheduler.activateForTesting(generated)
    state.markParticipant(player.id, generated.record.startsAt)

    // Indices 0 and 15 are opposite corners of a 4x4 board: never adjacent.
    val response = scheduler.submitWord(player.id, generated.record.id, listOf(0, 15))

    response.shouldBeInstanceOf<ServerMessage.WordRejected>()
    (response as ServerMessage.WordRejected).reason shouldBe RejectionReason.InvalidPath
  }

  test("submitting for a round id that is not the current one is rejected") {
    val clock = MutableGameClock(Instant.parse("2026-01-01T00:00:00Z"))
    val config = testServerConfig(roundDuration = 1.minutes, intermissionDuration = 1.minutes)
    val roomId = testRoomId()
    val scheduler = buildTestScheduler(database, clock, config, roomId)
    val player = PlayerRepository(database).insertGuest()

    val generated = RoundGenerationService(TestLexicon.lexicon, config, RoundRepository(database))
      .generateAndPersist(roomId, clock.now(), RoundTiming.endsAt(clock.now(), config.roundDuration))
    val state = scheduler.activateForTesting(generated)
    state.markParticipant(player.id, generated.record.startsAt)

    val response = scheduler.submitWord(player.id, "not-the-current-round", listOf(0, 1, 2))

    response.shouldBeInstanceOf<ServerMessage.WordRejected>()
  }

  test("joining mid-round with time to spare enters the round already in progress (ADR 0010)") {
    val clock = MutableGameClock(Instant.parse("2026-01-01T00:00:00Z"))
    val config =
      testServerConfig(roundDuration = 1.minutes, intermissionDuration = 1.minutes, lateJoinMinRemaining = 10.seconds)
    val roomId = testRoomId()
    val scheduler = buildTestScheduler(database, clock, config, roomId)
    val lateJoiner = PlayerRepository(database).insertGuest()

    val generated = RoundGenerationService(TestLexicon.lexicon, config, RoundRepository(database))
      .generateAndPersist(roomId, clock.now(), RoundTiming.endsAt(clock.now(), config.roundDuration))
    scheduler.activateForTesting(generated)

    clock.advance(30_000) // 30s into a 60s round: 30s left, well over the 10s threshold.
    val result = scheduler.join(lateJoiner.id)

    result.shouldBeInstanceOf<JoinResult.Started>()
    val started = result as JoinResult.Started
    started.message.roundId shouldBe generated.record.id
    started.message.startsAt shouldBe generated.record.startsAt.toEpochMilli()
    started.message.endsAt shouldBe generated.record.endsAt.toEpochMilli()
    started.message.alreadyFound shouldBe emptyList()
    started.message.runningScore shouldBe 0
    started.message.runningWords shouldBe 0

    // Their submissions are accepted like anyone else's, from this point on.
    val word = generated.solution.first()
    val submitResponse = scheduler.submitWord(lateJoiner.id, generated.record.id, word.path)
    submitResponse.shouldBeInstanceOf<ServerMessage.WordAccepted>()
  }

  test("a late joiner's entry time is when they joined, not when the round started") {
    val clock = MutableGameClock(Instant.parse("2026-01-01T00:00:00Z"))
    val config =
      testServerConfig(roundDuration = 1.minutes, intermissionDuration = 1.minutes, lateJoinMinRemaining = 10.seconds)
    val roomId = testRoomId()
    val scheduler = buildTestScheduler(database, clock, config, roomId)
    val player = PlayerRepository(database).insertGuest()

    val generated = RoundGenerationService(TestLexicon.lexicon, config, RoundRepository(database))
      .generateAndPersist(roomId, clock.now(), RoundTiming.endsAt(clock.now(), config.roundDuration))
    val state = scheduler.activateForTesting(generated)

    clock.advance(20_000)
    val joinInstant = clock.now()
    scheduler.join(player.id)

    state.entryTimeOf(player.id) shouldBe joinInstant
  }

  test("joining with exactly the threshold left still enters the round (the boundary is allowed)") {
    val clock = MutableGameClock(Instant.parse("2026-01-01T00:00:00Z"))
    val config =
      testServerConfig(roundDuration = 1.minutes, intermissionDuration = 1.minutes, lateJoinMinRemaining = 10.seconds)
    val roomId = testRoomId()
    val scheduler = buildTestScheduler(database, clock, config, roomId)
    val player = PlayerRepository(database).insertGuest()

    val generated = RoundGenerationService(TestLexicon.lexicon, config, RoundRepository(database))
      .generateAndPersist(roomId, clock.now(), RoundTiming.endsAt(clock.now(), config.roundDuration))
    scheduler.activateForTesting(generated)

    clock.set(generated.record.endsAt.minusSeconds(10)) // exactly the 10s threshold remaining.
    val result = scheduler.join(player.id)

    result.shouldBeInstanceOf<JoinResult.Started>()
  }

  test("joining with less than the threshold left waits for the next round instead (ADR 0010)") {
    val clock = MutableGameClock(Instant.parse("2026-01-01T00:00:00Z"))
    val config =
      testServerConfig(roundDuration = 1.minutes, intermissionDuration = 1.minutes, lateJoinMinRemaining = 10.seconds)
    val roomId = testRoomId()
    val scheduler = buildTestScheduler(database, clock, config, roomId)
    val player = PlayerRepository(database).insertGuest()

    val generated = RoundGenerationService(TestLexicon.lexicon, config, RoundRepository(database))
      .generateAndPersist(roomId, clock.now(), RoundTiming.endsAt(clock.now(), config.roundDuration))
    scheduler.activateForTesting(generated)

    clock.set(generated.record.endsAt.minusSeconds(10).plusMillis(1)) // one millisecond under the threshold.
    val result = scheduler.join(player.id)

    result.shouldBeInstanceOf<JoinResult.Waiting>()
  }

  test("finishing a round with no participants does nothing (no crash, no messages)") {
    val clock = MutableGameClock(Instant.parse("2026-01-01T00:00:00Z"))
    val config = testServerConfig(roundDuration = 500.milliseconds, intermissionDuration = 500.milliseconds)
    val roomId = testRoomId()
    val scheduler = buildTestScheduler(database, clock, config, roomId)

    val generated = RoundGenerationService(TestLexicon.lexicon, config, RoundRepository(database))
      .generateAndPersist(roomId, clock.now(), RoundTiming.endsAt(clock.now(), config.roundDuration))
    val state = scheduler.activateForTesting(generated)

    scheduler.finishForTesting(state)

    scheduler.activeRound shouldBe null
  }
})
