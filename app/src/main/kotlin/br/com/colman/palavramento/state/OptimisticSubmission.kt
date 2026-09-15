// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.state

import br.com.colman.palavramento.domain.WordNormalizer
import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Path
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.lexicon.toLexicon
import br.com.colman.palavramento.domain.protocol.FoundWord
import br.com.colman.palavramento.domain.submission.SubmissionResult
import br.com.colman.palavramento.domain.submission.SubmissionValidator
import kotlin.math.sqrt

/**
 * Optimistic client-side word validation (docs/adr/0014-validacao-otimista.md): runs the exact same
 * [SubmissionValidator] the server uses, over a
 * [br.com.colman.palavramento.domain.lexicon.Lexicon] built from the round's own solution
 * ([MatchUiState.InRound.validWords]), so the player sees a verdict the instant they release a
 * traced path instead of waiting on a round trip to the server.
 *
 * Pure and side-effect free, exactly like [MatchStateReducer]: no coroutines, no network, no
 * Android, so [decide] is unit-testable on the JVM.
 * [br.com.colman.palavramento.network.MultiplayerSession.submitWord] is the only caller - it applies
 * the returned [MatchUiState.InRound] to its own state and decides whether to still send to the
 * server.
 */
object OptimisticSubmission {

  /** What [br.com.colman.palavramento.network.MultiplayerSession.submitWord] should do with a just-released [path]. */
  sealed interface Decision {
    /** Apply [newState] right away (found word, score, flash/sound/haptics) and still send to the server. */
    data class Accept(val newState: MatchUiState.InRound) : Decision

    /** Apply [newState]'s rejection feedback and never send: `:domain`'s `OptimisticValidationTest`
     * is the argument the server would reject it too. */
    data class Reject(val newState: MatchUiState.InRound) : Decision

    /**
     * No local verdict possible - [MatchUiState.InRound.validWords] is empty (old server, or a
     * fallback) or the round is already over by the synced server clock - send to the server and
     * wait, exactly like before this feature existed.
     */
    data object Defer : Decision
  }

  /**
   * [nowMs] is the synced server clock's current estimate ([br.com.colman.palavramento.clock.ServerClock.nowMs]),
   * or null when no clock sample has arrived yet; null never blocks a local verdict, since there is
   * no evidence the round has ended.
   */
  // Two early-return guard clauses (no validWords, round already over) plus the validated outcome:
  // the same guard-clause style SubmissionValidator.validate documents and suppresses for.
  @Suppress("ReturnCount")
  fun decide(round: MatchUiState.InRound, path: List<Int>, nowMs: Long?): Decision {
    if (round.validWords.isEmpty()) return Decision.Defer
    if (nowMs != null && nowMs > round.endsAt) return Decision.Defer

    val board = boardOf(round.board)
    val lexicon = round.validWords.toLexicon()
    val alreadyFound = round.foundWords.mapTo(mutableSetOf()) { WordNormalizer.normalize(it.word) }

    return when (val result = SubmissionValidator.validate(board, lexicon, alreadyFound, Path(path))) {
      is SubmissionResult.Accepted -> Decision.Accept(
        round.copy(
          foundWords = round.foundWords + FoundWord(result.word, result.score, path),
          runningScore = round.runningScore + result.score,
          runningWords = round.runningWords + 1,
          lastFeedback = SubmissionFeedback.Accepted(result.word, result.score, path),
          pendingPaths = round.pendingPaths + setOf(path),
        ),
      )

      is SubmissionResult.Rejected -> Decision.Reject(
        round.copy(lastFeedback = SubmissionFeedback.Rejected(result.reason, path)),
      )
    }
  }

  private fun boardOf(tiles: List<Tile>): Board {
    val size = sqrt(tiles.size.toDouble()).toInt()
    return Board(size, tiles)
  }
}
