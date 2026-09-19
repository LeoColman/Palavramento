// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.round

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Path
import br.com.colman.palavramento.domain.lexicon.Lexicon
import br.com.colman.palavramento.domain.submission.RejectionReason
import br.com.colman.palavramento.domain.submission.SubmissionResult
import br.com.colman.palavramento.domain.submission.SubmissionValidator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * All the in-memory state one live round needs (dossier §12.4, §5): the persisted round plus, per
 * connected player, which words they already found. Kept in memory for speed (validating a
 * submission never round-trips the database), while [PlayerRoundState.submit] persists every
 * accepted word through [onAccepted] so a restart never loses them (dossier phase 3 task: "persist
 * as you go").
 */
class RoundState(val generated: GeneratedRound, private val onAccepted: suspend (AcceptedSubmission) -> Unit) {
  val record: RoundRecord get() = generated.record

  private val players = ConcurrentHashMap<String, PlayerRoundState>()

  /** Players present (joined) when this round started, or who reconnected mid-round (dossier §5.3). */
  private val participantIds = ConcurrentHashMap.newKeySet<String>()

  // When each participant actually started playing this round (ADR 0010: late join), so
  // RoundStatsCalculator.compute can measure secondsPerWord from a late joiner's own entry instead
  // of the round's own startsAt. Never persisted separately from `round_results.entered_at` (written
  // once, at finalize time): a restart mid-round loses this map along with every other live
  // in-memory connection, same as `RoundState` itself; the restart-recovery path in
  // RoomScheduler.activate re-marks a reconstructed participant with the round's own startsAt as an
  // approximation, documented there (same accepted-debt shape as ADR 0007's finalization-crash gap).
  private val entryTimes = ConcurrentHashMap<String, Instant>()

  fun markParticipant(playerId: String, entryTime: Instant) {
    participantIds.add(playerId)
    entryTimes[playerId] = entryTime
  }

  fun isParticipant(playerId: String): Boolean = playerId in participantIds

  fun participantSnapshot(): Set<String> = participantIds.toSet()

  /** [playerId]'s recorded entry time, or null if they were never marked a participant. */
  fun entryTimeOf(playerId: String): Instant? = entryTimes[playerId]

  fun playerState(playerId: String): PlayerRoundState = players.computeIfAbsent(playerId) { PlayerRoundState() }

  fun playerStateOrNull(playerId: String): PlayerRoundState? = players[playerId]

  /** Validates and, if accepted, records [pathIndices] for [playerId] (dossier §5.2), persisting it. */
  suspend fun submit(
    playerId: String,
    pathIndices: List<Int>,
    lexicon: Lexicon,
    acceptedAt: Instant,
  ): SubmitOutcome {
    val state = playerState(playerId)
    val outcome = state.submit(record.board, lexicon, pathIndices, acceptedAt)
    if (outcome is SubmitOutcome.Accepted) {
      onAccepted(
        AcceptedSubmission(
          record.id,
          playerId,
          outcome.normalized,
          outcome.display,
          outcome.score,
          outcome.path,
          acceptedAt
        ),
      )
    }
    return outcome
  }
}

/** Per-player progress within one [RoundState] (dossier §5.1: `runningScore`/`runningWords`). */
class PlayerRoundState {
  private val mutex = Mutex()
  private val found = LinkedHashMap<String, FoundWord>()
  private var runningScore = 0
  private var runningWords = 0

  /** Snapshot for `RoundStart.alreadyFound` on a reconnect (dossier §5.3), oldest submission first. */
  suspend fun snapshot(): PlayerRoundSnapshot = mutex.withLock {
    PlayerRoundSnapshot(found.values.toList(), runningScore, runningWords)
  }

  /** Restores state read back from `submissions` after a restart (dossier: "persist as you go"). */
  suspend fun restore(words: List<FoundWord>) = mutex.withLock {
    words.forEach { found[it.normalized] = it }
    runningScore = found.values.sumOf { it.score }
    runningWords = found.size
  }

  suspend fun submit(
    board: Board,
    lexicon: Lexicon,
    pathIndices: List<Int>,
    acceptedAt: Instant,
  ): SubmitOutcome = mutex.withLock {
    val alreadyFound = found.keys
    // The round's mutator is already baked into the tiles (ADR 0012), so validation needs only the board.
    val result = SubmissionValidator.validate(board, lexicon, alreadyFound, Path(pathIndices))
    when (result) {
      is SubmissionResult.Rejected -> SubmitOutcome.Rejected(result.reason, pathIndices)
      is SubmissionResult.Accepted -> {
        // result.normalized, not a re-spelling of the path (ADR 0015): a path over an alternatives
        // tile can spell more than one word, and only the validator's own result says which one this
        // submission actually matched.
        val normalized = result.normalized
        found[normalized] = FoundWord(normalized, result.word, result.score, pathIndices, acceptedAt)
        runningScore += result.score
        runningWords += 1
        SubmitOutcome.Accepted(normalized, result.word, result.score, pathIndices, runningScore, runningWords)
      }
    }
  }
}

data class PlayerRoundSnapshot(val found: List<FoundWord>, val runningScore: Int, val runningWords: Int)

data class FoundWord(
  val normalized: String,
  val display: String,
  val score: Int,
  val path: List<Int>,
  val acceptedAt: Instant,
)

/** Outcome of [PlayerRoundState.submit], enough to build both a wire message and a DB row. */
sealed interface SubmitOutcome {
  data class Accepted(
    val normalized: String,
    val display: String,
    val score: Int,
    val path: List<Int>,
    val runningScore: Int,
    val runningWords: Int,
  ) : SubmitOutcome

  data class Rejected(val reason: RejectionReason, val path: List<Int>) : SubmitOutcome
}

/** What [RoundState.submit] hands to persistence for a word that was just accepted. */
data class AcceptedSubmission(
  val roundId: String,
  val playerId: String,
  val normalized: String,
  val display: String,
  val score: Int,
  val path: List<Int>,
  val acceptedAt: Instant,
)
