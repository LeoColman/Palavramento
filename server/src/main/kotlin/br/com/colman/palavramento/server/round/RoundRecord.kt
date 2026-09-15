// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.round

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.solver.SolvedWord
import java.time.Instant

/** A persisted round row (`rounds` table), the server's own representation, not a wire DTO. */
data class RoundRecord(
  val id: String,
  val roomId: String,
  val seed: Long,
  val board: Board,
  val mutator: Mutator,
  val themeTitle: String,
  val themeSubtitle: String,
  val commonMin: Int,
  val maxScore: Int,
  val maxWords: Int,
  val startsAt: Instant,
  val endsAt: Instant,
  val status: String,
)

/** A freshly generated round together with its full solution, before or after persistence. */
data class GeneratedRound(val record: RoundRecord, val solution: List<SolvedWord>)

/** [RoundRecord.status] values, a round's lifecycle as [RoomScheduler] drives it. */
object RoundStatus {
  const val Scheduled = "SCHEDULED"
  const val Active = "ACTIVE"
  const val Finished = "FINISHED"
}

/** The single v1 room every player connects to (dossier §12.4: "a v1 usa uma sala global unica"). */
const val GlobalRoomId = "global"
