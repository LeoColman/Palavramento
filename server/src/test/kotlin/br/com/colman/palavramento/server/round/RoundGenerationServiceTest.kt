// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.round

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.generator.RoundDescriptorPicker
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.mutator.MutatorTheme
import br.com.colman.palavramento.domain.solver.SolvedWord
import br.com.colman.palavramento.domain.solver.WordTier
import br.com.colman.palavramento.server.repository.RoundRepository
import br.com.colman.palavramento.server.testsupport.TestLexicon
import br.com.colman.palavramento.server.testsupport.testDatabase
import br.com.colman.palavramento.server.testsupport.testRoomId
import br.com.colman.palavramento.server.testsupport.testServerConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant
import java.util.UUID

private fun fixtureBoard() = Board(4, List(16) { Tile("A", 1) })

/** An unpersisted [RoundRecord], for tests that build a [GeneratedRound] by hand instead of solving a real board. */
private fun fixtureRecord(
  roomId: String,
  id: String = UUID.randomUUID().toString(),
  startsAt: Instant,
  endsAt: Instant,
  status: String = RoundStatus.Scheduled,
) = RoundRecord(
  id = id,
  roomId = roomId,
  seed = 1L,
  board = fixtureBoard(),
  mutator = Mutator.NoMutator,
  themeTitle = "Grade padrão",
  themeSubtitle = "0 palavras comuns",
  commonMin = 0,
  maxScore = 6,
  maxWords = 1,
  startsAt = startsAt,
  endsAt = endsAt,
  status = status,
)

private fun fixtureSolution() = listOf(SolvedWord("SOL", "sol", listOf(0, 1, 2), 6, WordTier.Common))

/**
 * [RoundGenerationService] against a real database and the real lexicon (dossier §3: "pre-geradas
 * com pelo menos uma de antecedencia"). [generateAndPersist] itself is exercised through a fixed seed
 * so the mutator/theme/commonMin it derives can be checked against [RoundDescriptorPicker] and
 * [MutatorTheme] directly, rather than merely asserting the call did not throw.
 */
class RoundGenerationServiceTest : FunSpec({
  val database = testDatabase()

  test("generateAndPersist derives mutator, theme and commonMin from RoundDescriptorPicker for the given seed") {
    val config = testServerConfig()
    val roomId = testRoomId()
    val seed = 4242L
    val service = RoundGenerationService(TestLexicon.lexicon, config, RoundRepository(database)) { seed }
    val startsAt = Instant.parse("2026-01-01T00:00:00Z")

    val generated = service.generateAndPersist(roomId, startsAt, RoundTiming.endsAt(startsAt, config.roundDuration))

    val expected = RoundDescriptorPicker.pick(seed)
    generated.record.seed shouldBe seed
    generated.record.mutator shouldBe expected.mutator
    generated.record.themeTitle shouldBe MutatorTheme.title(expected.mutator)
    generated.record.themeSubtitle shouldBe MutatorTheme.subtitle(expected.commonMin)
    generated.record.commonMin shouldBe expected.commonMin
  }

  test("generateAndPersist sets maxScore/maxWords from the solution and the room/window/status as requested") {
    val config = testServerConfig()
    val roomId = testRoomId()
    val service = RoundGenerationService(TestLexicon.lexicon, config, RoundRepository(database))
    val startsAt = Instant.parse("2026-02-01T00:00:00Z")
    val endsAt = RoundTiming.endsAt(startsAt, config.roundDuration)

    val generated = service.generateAndPersist(roomId, startsAt, endsAt)

    generated.solution.shouldNotBeEmpty()
    generated.record.maxScore shouldBe generated.solution.sumOf { it.score }
    generated.record.maxWords shouldBe generated.solution.size
    generated.record.roomId shouldBe roomId
    generated.record.startsAt shouldBe startsAt
    generated.record.endsAt shouldBe endsAt
    generated.record.status shouldBe RoundStatus.Scheduled
  }

  test("generateAndPersist gives every round its own fresh id") {
    val config = testServerConfig()
    val roomId = testRoomId()
    val service = RoundGenerationService(TestLexicon.lexicon, config, RoundRepository(database))
    val startsAt = Instant.parse("2026-03-01T00:00:00Z")

    val first = service.generateAndPersist(roomId, startsAt, RoundTiming.endsAt(startsAt, config.roundDuration))
    val secondStartsAt = first.record.endsAt.plusSeconds(60)
    val second = service.generateAndPersist(
      roomId,
      secondStartsAt,
      RoundTiming.endsAt(secondStartsAt, config.roundDuration),
    )

    first.record.id shouldNotBe second.record.id
    UUID.fromString(first.record.id).shouldNotBeNull()
    UUID.fromString(second.record.id).shouldNotBeNull()
  }

  test("generateAndPersist writes the round and its solution so both round-trip exactly through the repository") {
    val config = testServerConfig()
    val roomId = testRoomId()
    val roundRepository = RoundRepository(database)
    val service = RoundGenerationService(TestLexicon.lexicon, config, roundRepository)
    val startsAt = Instant.parse("2026-04-01T00:00:00Z")

    val generated = service.generateAndPersist(roomId, startsAt, RoundTiming.endsAt(startsAt, config.roundDuration))

    roundRepository.findById(generated.record.id) shouldBe generated.record
    roundRepository.loadSolution(generated.record.id).toSet() shouldBe generated.solution.toSet()
  }

  test("recoverOrGenerateCurrent generates and persists a first round when the room has none yet") {
    val config = testServerConfig()
    val roomId = testRoomId()
    val roundRepository = RoundRepository(database)
    val service = RoundGenerationService(TestLexicon.lexicon, config, roundRepository)
    val now = Instant.parse("2026-05-01T00:00:00Z")

    val current = service.recoverOrGenerateCurrent(roomId, now)

    val expectedStartsAt = RoundTiming.firstRoundStartsAt(now, config.intermissionDuration)
    current.record.startsAt shouldBe expectedStartsAt
    current.record.endsAt shouldBe RoundTiming.endsAt(expectedStartsAt, config.roundDuration)
    current.record.roomId shouldBe roomId
    roundRepository.findPending(roomId, limit = 10).map { it.id } shouldBe listOf(current.record.id)
  }

  test("recoverOrGenerateCurrent resumes the oldest pending round instead of generating a duplicate") {
    val config = testServerConfig()
    val roomId = testRoomId()
    val roundRepository = RoundRepository(database)
    val service = RoundGenerationService(TestLexicon.lexicon, config, roundRepository)
    val base = Instant.parse("2026-06-01T00:00:00Z")
    val earliest = fixtureRecord(roomId, startsAt = base, endsAt = base.plusSeconds(60))
    val later = fixtureRecord(roomId, startsAt = base.plusSeconds(600), endsAt = base.plusSeconds(660))
    val earliestSolution = fixtureSolution()
    roundRepository.insert(earliest, earliestSolution)
    roundRepository.insert(later, emptyList())

    val current = service.recoverOrGenerateCurrent(roomId, base.minusSeconds(1000))

    current.record shouldBe earliest
    current.solution shouldBe earliestSolution
    roundRepository.findPending(roomId, limit = 10) shouldHaveSize 2
  }

  test("recoverOrGenerateNext generates the round after current when nothing is pre-generated yet") {
    val config = testServerConfig()
    val roomId = testRoomId()
    val roundRepository = RoundRepository(database)
    val service = RoundGenerationService(TestLexicon.lexicon, config, roundRepository)
    val startsAt = Instant.parse("2026-07-01T00:00:00Z")
    val current = service.generateAndPersist(roomId, startsAt, RoundTiming.endsAt(startsAt, config.roundDuration))

    val next = service.recoverOrGenerateNext(roomId, current)

    val expectedStartsAt = RoundTiming.nextRoundStartsAt(current.record.endsAt, config.intermissionDuration)
    next.record.startsAt shouldBe expectedStartsAt
    next.record.endsAt shouldBe RoundTiming.endsAt(expectedStartsAt, config.roundDuration)
    next.record.roomId shouldBe roomId
    next.record.id shouldNotBe current.record.id
    roundRepository.findPending(roomId, limit = 10) shouldHaveSize 2
  }

  test("recoverOrGenerateNext returns the already-persisted next round without generating a duplicate") {
    val config = testServerConfig()
    val roomId = testRoomId()
    val roundRepository = RoundRepository(database)
    val service = RoundGenerationService(TestLexicon.lexicon, config, roundRepository)
    val base = Instant.parse("2026-08-01T00:00:00Z")
    val current = fixtureRecord(roomId, startsAt = base, endsAt = base.plusSeconds(60))
    val next = fixtureRecord(roomId, startsAt = base.plusSeconds(600), endsAt = base.plusSeconds(660))
    val nextSolution = fixtureSolution()
    roundRepository.insert(current, emptyList())
    roundRepository.insert(next, nextSolution)

    val result = service.recoverOrGenerateNext(roomId, GeneratedRound(current, emptyList()))

    result.record shouldBe next
    result.solution shouldBe nextSolution
    roundRepository.findPending(roomId, limit = 10) shouldHaveSize 2
  }

  test("recoverOrGenerateNext regenerates when the second pending entry is current itself, not a real next round") {
    val config = testServerConfig()
    val roomId = testRoomId()
    val roundRepository = RoundRepository(database)
    val service = RoundGenerationService(TestLexicon.lexicon, config, roundRepository)
    val base = Instant.parse("2026-09-01T00:00:00Z")
    // An older round the caller does not consider "current" (e.g. left over from a restart), plus
    // the caller's own "current" round: findPending(roomId, 2) then returns [olderLeftover, current],
    // so pending.getOrNull(1).id equals current's own id rather than a genuine next round.
    val olderLeftover = fixtureRecord(roomId, startsAt = base, endsAt = base.plusSeconds(60))
    val current = fixtureRecord(roomId, startsAt = base.plusSeconds(1000), endsAt = base.plusSeconds(1060))
    roundRepository.insert(olderLeftover, emptyList())
    roundRepository.insert(current, emptyList())

    val result = service.recoverOrGenerateNext(roomId, GeneratedRound(current, emptyList()))

    val expectedStartsAt = RoundTiming.nextRoundStartsAt(current.endsAt, config.intermissionDuration)
    result.record.id shouldNotBe olderLeftover.id
    result.record.id shouldNotBe current.id
    result.record.startsAt shouldBe expectedStartsAt
    roundRepository.findPending(roomId, limit = 10) shouldHaveSize 3
  }
})
