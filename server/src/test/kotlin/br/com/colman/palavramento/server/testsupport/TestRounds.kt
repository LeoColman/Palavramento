// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.testsupport

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.server.repository.RoundRepository
import br.com.colman.palavramento.server.round.RoundRecord
import br.com.colman.palavramento.server.round.RoundStatus
import java.time.Instant
import java.util.UUID

/**
 * Inserts a finished round with a throwaway board and no solution: fixture data for tests that only
 * need a valid `rounds.id` to attach `round_results`/`submissions` to (auth migration tests), not a
 * playable round.
 */
suspend fun RoundRepository.insertFakeFinishedRound(roomId: String): String {
  val id = UUID.randomUUID().toString()
  val board = Board(4, List(16) { Tile("A", 1) })
  val now = Instant.now()
  val record = RoundRecord(
    id = id,
    roomId = roomId,
    seed = 0L,
    board = board,
    mutator = Mutator.NoMutator,
    themeTitle = "Grade padrão",
    themeSubtitle = "0 palavras comuns",
    commonMin = 0,
    maxScore = 0,
    maxWords = 0,
    startsAt = now,
    endsAt = now.plusSeconds(1),
    status = RoundStatus.Finished,
  )
  insert(record, emptyList())
  return id
}
