// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.state

import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.protocol.FoundWord
import br.com.colman.palavramento.domain.protocol.LabelledWord
import br.com.colman.palavramento.domain.protocol.ServerMessage
import br.com.colman.palavramento.domain.protocol.ValidWord
import br.com.colman.palavramento.domain.stats.RoundStats
import br.com.colman.palavramento.domain.submission.RejectionReason

/**
 * Everything a room-scoped screen (Waiting, Match, Results, Leaderboard) needs to render, derived
 * purely from the `/ws/multiplayer` message stream (dossier 5) by [MatchStateReducer]. One instance
 * lives in [br.com.colman.palavramento.network.MultiplayerSession.state] for the lifetime of a room
 * membership; a reconnect folds straight back into [InRound] or [Lobby], never a separate state.
 */
sealed interface MatchUiState {

  /** No `RoundStart`/`LobbyState` received yet for this connection (still handshaking). */
  data object Disconnected : MatchUiState

  /** Between rounds, or waiting for the very first one (dossier 6.1 "Waiting"). */
  data class Lobby(val nextRoundStartsAt: Long, val playersWaiting: Int) : MatchUiState

  /**
   * A round in progress (dossier 6.2). [foundWords] starts from `RoundStart.alreadyFound`, so a
   * client that reconnects mid-round restores exactly the words it had already found (dossier 5.3).
   *
   * [validWords] is `RoundStart.validWords` (ADR 0014): the round's own solution, empty for an old
   * server or as a fallback, in which case [br.com.colman.palavramento.state.OptimisticSubmission]
   * never produces a local verdict and every submission waits for the server as before this feature.
   * [pendingPaths] is the set of just-submitted paths this client accepted locally but the server has
   * not confirmed yet (matched against `WordAccepted`/`WordRejected.path`, which the server always
   * echoes): reset to empty by any fresh `RoundStart`, fresh round or reconnect alike, since a
   * reconnect's `alreadyFound`/`runningScore`/`runningWords` already reflect the server's own
   * authoritative state.
   */
  data class InRound(
    val roundId: String,
    val board: List<Tile>,
    val mutator: Mutator,
    val themeTitle: String,
    val themeSubtitle: String,
    val maxScore: Int,
    val maxWords: Int,
    val startsAt: Long,
    val endsAt: Long,
    val foundWords: List<FoundWord>,
    val runningScore: Int,
    val runningWords: Int,
    val lastFeedback: SubmissionFeedback? = null,
    val validWords: List<ValidWord> = emptyList(),
    val pendingPaths: Set<List<Int>> = emptySet(),
  ) : MatchUiState

  /**
   * Results (dossier 6.3) and, once it arrives, Leaderboard (dossier 6.4) for a finished round.
   * [nextRoundStartsAt] and [playersWaiting] are filled in by the `LobbyState` messages the server
   * keeps sending while this screen is up, so "Proxima partida em MM:SS" stays live without a
   * dedicated message type. `ServerMessage.RoundEnd` has no `maxScore`/`maxWords` of its own (dossier
   * 6.3's "73/4193" denominator), so [MatchStateReducer] carries them over from the [InRound] state
   * the round just came from instead of the protocol needing a new field for it. [board] and
   * [mutator] are carried over the same way, for the results mini board (dossier 6.3).
   */
  data class PostRound(
    val roundId: String,
    val board: List<Tile>,
    val mutator: Mutator,
    val stats: RoundStats,
    val words: List<LabelledWord>,
    val maxScore: Int,
    val maxWords: Int,
    val leaderboard: ServerMessage.Leaderboard? = null,
    val nextRoundStartsAt: Long? = null,
    val playersWaiting: Int = 0,
  ) : MatchUiState
}

/**
 * Feedback for the last `SubmitWord` result (dossier 6.2: color + haptics on accept/reject).
 * [path] is the server-echoed tile path (dossier 5.1: both `WordAccepted` and `WordRejected` carry
 * it), used by the board (task brief 2) to flash exactly the tiles that were submitted, not just
 * show a text line.
 */
sealed interface SubmissionFeedback {
  data class Accepted(val word: String, val score: Int, val path: List<Int>) : SubmissionFeedback
  data class Rejected(val reason: RejectionReason, val path: List<Int>) : SubmissionFeedback
}
