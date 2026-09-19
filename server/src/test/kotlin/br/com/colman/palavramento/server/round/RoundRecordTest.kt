// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.round

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.solver.SolvedWord
import br.com.colman.palavramento.domain.solver.WordTier
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * [RoundRecord] and [GeneratedRound] are plain data, so this spec reads every property once (the
 * CLAUDE.md Pitest rule: comparing by equality alone does not count a getter as covered), plus the
 * lifecycle string constants [RoundRepository] persists literally and [RoomScheduler] compares
 * against, and [GlobalRoomId].
 */
class RoundRecordTest : FunSpec({
  test("every RoundRecord property reads back exactly what the constructor was given") {
    val board = Board(4, List(16) { Tile("A", 1) })
    val startsAt = Instant.parse("2026-01-01T00:00:00Z")
    val endsAt = startsAt.plusSeconds(120)

    val record = RoundRecord(
      id = "round-1",
      roomId = "room-1",
      seed = 99L,
      board = board,
      mutator = Mutator.ValuableLetter('L', 10),
      themeTitle = "L de alto valor",
      themeSubtitle = "10 palavras comuns",
      commonMin = 10,
      maxScore = 500,
      maxWords = 42,
      startsAt = startsAt,
      endsAt = endsAt,
      status = RoundStatus.Active,
    )

    record.id shouldBe "round-1"
    record.roomId shouldBe "room-1"
    record.seed shouldBe 99L
    record.board shouldBe board
    record.mutator shouldBe Mutator.ValuableLetter('L', 10)
    record.themeTitle shouldBe "L de alto valor"
    record.themeSubtitle shouldBe "10 palavras comuns"
    record.commonMin shouldBe 10
    record.maxScore shouldBe 500
    record.maxWords shouldBe 42
    record.startsAt shouldBe startsAt
    record.endsAt shouldBe endsAt
    record.status shouldBe RoundStatus.Active
  }

  test("GeneratedRound exposes the record and solution it was built from") {
    val record = RoundRecord(
      id = "round-2",
      roomId = "room-2",
      seed = 1L,
      board = Board(4, List(16) { Tile("A", 1) }),
      mutator = Mutator.NoMutator,
      themeTitle = "Grade padrão",
      themeSubtitle = "0 palavras comuns",
      commonMin = 0,
      maxScore = 6,
      maxWords = 1,
      startsAt = Instant.EPOCH,
      endsAt = Instant.EPOCH.plusSeconds(60),
      status = RoundStatus.Scheduled,
    )
    val solution = listOf(SolvedWord("SOL", "sol", listOf(0, 1, 2), 6, WordTier.Common))

    val generated = GeneratedRound(record, solution)

    generated.record shouldBe record
    generated.solution shouldBe solution
  }

  test("RoundStatus constants are the exact strings persisted to the rounds table") {
    RoundStatus.Scheduled shouldBe "SCHEDULED"
    RoundStatus.Active shouldBe "ACTIVE"
    RoundStatus.Finished shouldBe "FINISHED"
  }

  test("GlobalRoomId is the single v1 room id (dossier 12.4)") {
    GlobalRoomId shouldBe "global"
  }
})
