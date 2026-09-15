// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.protocol

import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.solver.WordTier
import br.com.colman.palavramento.domain.stats.RoundStats
import br.com.colman.palavramento.domain.submission.RejectionReason
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Every message the server can send over `/ws/multiplayer` (dossier 5.1). */
@Serializable
sealed interface ServerMessage {

  @Serializable
  @SerialName("LobbyState")
  data class LobbyState(val nextRoundStartsAt: Long, val playersWaiting: Int) : ServerMessage

  /**
   * Starts a round for every player in the room at the same [startsAt]/[endsAt] (dossier 1.4/5.1).
   *
   * [alreadyFound], [runningScore] and [runningWords] are additions beyond the dossier's payload
   * table, required by its own prose in 5.3: a client that reconnects mid-round must receive "o
   * estado da rodada corrente incluindo palavras ja aceitas". Reusing `RoundStart` for that (instead
   * of a separate resync message) means a reconnecting client and a client joining on time run
   * through the exact same code path; the three fields default to empty/zero for a fresh round.
   * Documented in `docs/adr/0005-protocolo.md`.
   *
   * [validWords] is the round's full pre-computed solution (the same set persisted in
   * `round_words`), added by ADR 0014 so the client can validate a submission locally, with the
   * exact same [br.com.colman.palavramento.domain.submission.SubmissionValidator] the server uses,
   * instead of waiting for the round trip. Sent in every `RoundStart` (fresh join, late join,
   * reconnect). Defaults to empty so an old server (which never sets it) and an old client (which
   * ignores it, `ignoreUnknownKeys`) stay compatible: an empty list means "no local verdict, wait for
   * the server", exactly today's behavior.
   */
  @Serializable
  @SerialName("RoundStart")
  data class RoundStart(
    val roundId: String,
    val board: List<Tile>,
    val mutator: Mutator,
    val themeTitle: String,
    val themeSubtitle: String,
    val maxScore: Int,
    val maxWords: Int,
    val startsAt: Long,
    val endsAt: Long,
    val alreadyFound: List<FoundWord> = emptyList(),
    val runningScore: Int = 0,
    val runningWords: Int = 0,
    val validWords: List<ValidWord> = emptyList(),
  ) : ServerMessage

  @Serializable
  @SerialName("WordAccepted")
  data class WordAccepted(
    val word: String,
    val score: Int,
    val runningScore: Int,
    val runningWords: Int,
    val path: List<Int>,
  ) : ServerMessage

  @Serializable
  @SerialName("WordRejected")
  data class WordRejected(val reason: RejectionReason, val path: List<Int>) : ServerMessage

  @Serializable
  @SerialName("RoundEnd")
  data class RoundEnd(val roundId: String, val stats: RoundStats, val words: List<LabelledWord>) : ServerMessage

  @Serializable
  @SerialName("Leaderboard")
  data class Leaderboard(
    val players: List<LeaderboardRow>,
    val self: LeaderboardRow,
    val percentile: Int,
    val totalPlayers: Int,
  ) : ServerMessage

  /**
   * Answers a [ClientMessage.ClockSync]. Not in the dossier's payload table: added because 5.3
   * requires the client to sync its clock offset with the server during the handshake and never
   * drive the round countdown off its own clock. Documented in `docs/adr/0005-protocolo.md`.
   */
  @Serializable
  @SerialName("ClockSyncResponse")
  data class ClockSyncResponse(val clientSentAt: Long, val serverTime: Long) : ServerMessage
}

/** A word the player already found before this message was sent, replayed to a reconnecting client. */
@Serializable
data class FoundWord(val word: String, val score: Int, val path: List<Int>)

/**
 * One word of the round's pre-computed solution (ADR 0014), carried by every `RoundStart` so the
 * client can build a local [br.com.colman.palavramento.domain.lexicon.Lexicon] and validate a
 * submission with the same rules the server uses. [normalized] is the tile-spelled A-Z form
 * ([br.com.colman.palavramento.domain.board.Path.spell]'s output), [display] the lexicon's own
 * accented display form.
 */
@Serializable
data class ValidWord(val normalized: String, val display: String)

/** One row of the full, score-sorted solved word list `RoundEnd` sends (dossier 6.3). */
@Serializable
data class LabelledWord(
  val word: String,
  val score: Int,
  val tier: WordTier,
  val path: List<Int>,
  val found: Boolean,
)

/** One row of a `Leaderboard` message (dossier 6.4). */
@Serializable
data class LeaderboardRow(val rank: Int, val name: String, val score: Int, val words: Int)
