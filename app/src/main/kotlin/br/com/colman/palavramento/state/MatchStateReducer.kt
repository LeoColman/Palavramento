// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.state

import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.protocol.FoundWord
import br.com.colman.palavramento.domain.protocol.ServerMessage

/**
 * Folds one [ServerMessage] into the previous [MatchUiState]. Pure and side-effect free so the
 * whole room-screen state machine (LobbyState -> RoundStart -> WordAccepted/WordRejected ->
 * RoundEnd -> Leaderboard, and the mid-round reconnect where `RoundStart` carries `alreadyFound`)
 * is unit-testable without a network connection.
 */
object MatchStateReducer {

  fun reduce(state: MatchUiState, message: ServerMessage): MatchUiState = when (message) {
    is ServerMessage.LobbyState -> onLobbyState(state, message)
    is ServerMessage.RoundStart -> onRoundStart(message)
    is ServerMessage.WordAccepted -> onWordAccepted(state, message)
    is ServerMessage.WordRejected -> onWordRejected(state, message)
    is ServerMessage.RoundEnd -> onRoundEnd(state, message)
    is ServerMessage.Leaderboard -> onLeaderboard(state, message)
    // Clock sync is a side channel consumed by the session before it reaches the reducer
    // (docs/adr/0006-arquitetura-do-app.md); passing through keeps the fold total either way.
    is ServerMessage.ClockSyncResponse -> state
    // A message a newer server sent that this build has no case for (ADR 0018): ignoring it keeps
    // the match running on everything else the server says.
    is ServerMessage.Unknown -> state
  }

  private fun onLobbyState(state: MatchUiState, message: ServerMessage.LobbyState): MatchUiState =
    if (state is MatchUiState.PostRound) {
      state.copy(nextRoundStartsAt = message.nextRoundStartsAt, playersWaiting = message.playersWaiting)
    } else {
      MatchUiState.Lobby(message.nextRoundStartsAt, message.playersWaiting)
    }

  private fun onRoundStart(message: ServerMessage.RoundStart): MatchUiState.InRound = MatchUiState.InRound(
    roundId = message.roundId,
    board = message.board,
    mutator = message.mutator,
    themeTitle = message.themeTitle,
    themeSubtitle = message.themeSubtitle,
    maxScore = message.maxScore,
    maxWords = message.maxWords,
    startsAt = message.startsAt,
    endsAt = message.endsAt,
    foundWords = message.alreadyFound,
    runningScore = message.runningScore,
    runningWords = message.runningWords,
    validWords = message.validWords,
    // Rebuilding InRound from scratch here already clears any locally-accepted, unconfirmed word
    // (ADR 0014): a fresh round has none to carry over, and a reconnect's alreadyFound/runningScore/
    // runningWords above are already the server's authoritative reply, so nothing was left pending.
  )

  /**
   * [message.path] in [MatchUiState.InRound.pendingPaths] means this confirms a word
   * [br.com.colman.palavramento.state.OptimisticSubmission] already applied to [foundWords]/
   * [runningScore]/[runningWords] locally (ADR 0014): only the running totals (now authoritative)
   * and [pendingPaths] change, [lastFeedback] is left untouched so the accept flash/sound/haptics
   * never fire a second time for the same word. Otherwise this is a genuinely new accept (no local
   * verdict was possible, or a fallback server/client pair), handled exactly as before this feature.
   */
  private fun onWordAccepted(state: MatchUiState, message: ServerMessage.WordAccepted): MatchUiState =
    if (state is MatchUiState.InRound) {
      if (message.path in state.pendingPaths) {
        state.copy(
          runningScore = message.runningScore,
          runningWords = message.runningWords,
          pendingPaths = state.pendingPaths - setOf(message.path),
        )
      } else {
        state.copy(
          foundWords = state.foundWords + FoundWord(message.word, message.score, message.path),
          runningScore = message.runningScore,
          runningWords = message.runningWords,
          lastFeedback = SubmissionFeedback.Accepted(message.word, message.score, message.path),
        )
      }
    } else {
      state
    }

  /**
   * [message.path] in [MatchUiState.InRound.pendingPaths] means the server disagreed with a word
   * this client accepted locally (ADR 0014): rare (the equivalence property test in `:domain` is the
   * argument it should not happen), so this rolls the optimistic accept back - removes it from
   * [foundWords], subtracts its score and word count - and shows the rejection like any other one.
   * Otherwise this is an ordinary rejection, unchanged from before this feature.
   */
  private fun onWordRejected(state: MatchUiState, message: ServerMessage.WordRejected): MatchUiState =
    if (state is MatchUiState.InRound) {
      if (message.path in state.pendingPaths) {
        val rolledBack = state.foundWords.firstOrNull { it.path == message.path }
        state.copy(
          foundWords = state.foundWords.filterNot { it.path == message.path },
          runningScore = state.runningScore - (rolledBack?.score ?: 0),
          runningWords = state.runningWords - if (rolledBack != null) 1 else 0,
          pendingPaths = state.pendingPaths - setOf(message.path),
          lastFeedback = SubmissionFeedback.Rejected(message.reason, message.path),
        )
      } else {
        state.copy(lastFeedback = SubmissionFeedback.Rejected(message.reason, message.path))
      }
    } else {
      state
    }

  private fun onRoundEnd(state: MatchUiState, message: ServerMessage.RoundEnd): MatchUiState.PostRound {
    val justEnded = state as? MatchUiState.InRound
    return MatchUiState.PostRound(
      roundId = message.roundId,
      board = justEnded?.board.orEmpty(),
      mutator = justEnded?.mutator ?: Mutator.NoMutator,
      stats = message.stats,
      words = message.words,
      maxScore = justEnded?.maxScore ?: message.stats.points,
      maxWords = justEnded?.maxWords ?: message.stats.words,
    )
  }

  private fun onLeaderboard(state: MatchUiState, message: ServerMessage.Leaderboard): MatchUiState =
    if (state is MatchUiState.PostRound) state.copy(leaderboard = message) else state
}
