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
  )

  private fun onWordAccepted(state: MatchUiState, message: ServerMessage.WordAccepted): MatchUiState =
    if (state is MatchUiState.InRound) {
      state.copy(
        foundWords = state.foundWords + FoundWord(message.word, message.score, message.path),
        runningScore = message.runningScore,
        runningWords = message.runningWords,
        lastFeedback = SubmissionFeedback.Accepted(message.word, message.score, message.path),
      )
    } else {
      state
    }

  private fun onWordRejected(state: MatchUiState, message: ServerMessage.WordRejected): MatchUiState =
    if (state is MatchUiState.InRound) {
      state.copy(lastFeedback = SubmissionFeedback.Rejected(message.reason, message.path))
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
