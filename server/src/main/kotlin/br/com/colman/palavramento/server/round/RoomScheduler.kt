// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.round

import br.com.colman.palavramento.domain.lexicon.Lexicon
import br.com.colman.palavramento.domain.protocol.LabelledWord
import br.com.colman.palavramento.domain.protocol.LeaderboardRow
import br.com.colman.palavramento.domain.protocol.ServerMessage
import br.com.colman.palavramento.domain.protocol.ValidWord
import br.com.colman.palavramento.domain.solver.SolvedWord
import br.com.colman.palavramento.domain.stats.Percentile
import br.com.colman.palavramento.domain.submission.RejectionReason
import br.com.colman.palavramento.server.config.ServerConfig
import br.com.colman.palavramento.server.repository.RoundRepository
import br.com.colman.palavramento.server.repository.SubmissionRepository
import br.com.colman.palavramento.server.ws.ConnectionRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import br.com.colman.palavramento.domain.protocol.FoundWord as WireFoundWord

private val logger = LoggerFactory.getLogger("RoomScheduler")

/** How long [RoomScheduler] waits before restarting a round cycle that failed. */
private val DefaultFailureBackoff = 5.seconds

/** [RoomScheduler.join]'s two possible answers to a `JoinRoom` (dossier §1.4/§5.3). */
sealed interface JoinResult {
  data class Started(val message: ServerMessage.RoundStart) : JoinResult
  data class Waiting(val message: ServerMessage.LobbyState) : JoinResult
}

/**
 * Drives the single global room's round cycle end to end (dossier §1.4, §12.4): pre-generates
 * rounds one ahead, starts and ends them on schedule, keeps the live [RoundState], and answers
 * joins and submissions for [br.com.colman.palavramento.server.ws] to turn into wire messages.
 *
 * A plain injected [clock] (not `delay`'s own notion of time) drives every timestamp this class
 * puts on the wire or compares against, so the round window is always defined in terms of the same
 * clock a fake could replace in a unit test (dossier §5.3, phase 3 task: "scheduler driven by an
 * injectable clock"); [waitUntil] still suspends via real [delay], since only [SystemGameClock] ever
 * drives the live loop; a fake clock is for pure timing-math unit tests, never for running this loop.
 */
@Suppress("LongParameterList") // every parameter is a distinct injected singleton (Koin wiring), not a group to bundle.
class RoomScheduler(
  private val config: ServerConfig,
  private val clock: GameClock,
  private val roundRepository: RoundRepository,
  private val roundGenerationService: RoundGenerationService,
  private val submissionRepository: SubmissionRepository,
  private val roundFinalizer: RoundFinalizer,
  private val lexicon: Lexicon,
  val connectionRegistry: ConnectionRegistry,
  private val roomId: String = GlobalRoomId,
  // Injectable so a test can prove the loop survives a failed cycle without waiting out the real one.
  private val failureBackoff: kotlin.time.Duration = DefaultFailureBackoff,
) {
  @Volatile
  private var currentRoundState: RoundState? = null

  @Volatile
  var nextRoundStartsAt: Instant = clock.now()
    private set

  val activeRound: RoundState? get() = currentRoundState

  /** Launches the round loop in [scope]; cancelling the returned [Job] stops it. */
  fun start(scope: CoroutineScope): Job = scope.launch { runLoop() }

  /**
   * Test seam: activates [generated] as the current round without running [runLoop]'s real-time
   * wait, so timing-sensitive behavior ([submitWord]'s tolerance window, [join]'s participant check)
   * can be exercised deterministically against a real database and lexicon, with a [GameClock] the
   * test fully controls, instead of racing the real clock through a live round.
   */
  internal suspend fun activateForTesting(generated: GeneratedRound): RoundState = activate(generated)

  /** Test seam: the [finish] half of the round lifecycle, paired with [activateForTesting]. */
  internal suspend fun finishForTesting(state: RoundState) {
    finish(state)
    currentRoundState = null
  }

  /**
   * Keeps [runRounds] running for as long as the scope lives. A round that fails takes its own
   * cycle down and nothing else: on 2026-09-25 a player deleted their account mid-round (ADR 0020),
   * the foreign key rejected their `round_results` row, and the exception travelled out of [finish],
   * out of the loop and out of the coroutine. The process stayed up answering HTTP and serving
   * metrics for half an hour without ever starting another round, and every client sat on "Proxima
   * partida em 00:00" until someone restarted the server by hand.
   *
   * So a failed cycle is logged and retried instead of ending the room. [runRounds] is re-entered
   * from scratch, reading the schedule back out of the database rather than trusting the rounds the
   * failed cycle had in hand, and [failureBackoff] keeps a failure that returns immediately (the
   * database being unreachable, say) from turning this into a hot loop.
   */
  @Suppress("TooGenericExceptionCaught") // Anything a cycle throws has to stay inside it; that is the point.
  private suspend fun runLoop() {
    while (currentCoroutineContext().isActive) {
      try {
        runRounds()
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (failure: Exception) {
        logger.error("Round cycle failed for room $roomId, restarting it in $failureBackoff", failure)
        currentRoundState = null
        delay(failureBackoff)
      }
    }
  }

  private suspend fun runRounds() {
    var current = roundGenerationService.recoverOrGenerateCurrent(roomId, clock.now())
    var next = roundGenerationService.recoverOrGenerateNext(roomId, current)
    nextRoundStartsAt = current.record.startsAt

    while (currentCoroutineContext().isActive) {
      waitUntil(current.record.startsAt)
      val state = activate(current)
      nextRoundStartsAt = next.record.startsAt

      // Wait through the late-submission tolerance window too (dossier §5.2), not just to endsAt:
      // otherwise a message that legitimately arrives late-but-tolerated could find the round
      // already finished and nulled out, instead of being validated as this round's own submission.
      waitUntil(RoundTiming.lateSubmissionDeadline(current.record.endsAt, config.lateSubmissionTolerance))
      finish(state)
      currentRoundState = null
      connectionRegistry.broadcast(
        ServerMessage.LobbyState(next.record.startsAt.toEpochMilli(), connectionRegistry.connectedPlayerCount()),
      )

      current = next
      next = roundGenerationService.generateNext(current)
      nextRoundStartsAt = current.record.startsAt
    }
  }

  /** Validates and, if accepted, records [path] for [playerId]; null means silently discard it. */
  @Suppress("ReturnCount") // guard-clause style: each early return is one distinct outcome (dossier §5.2).
  suspend fun submitWord(playerId: String, roundId: String, path: List<Int>): ServerMessage? {
    val state = currentRoundState
    if (state == null || state.record.id != roundId) {
      return ServerMessage.WordRejected(RejectionReason.InvalidPath, path)
    }

    val now = clock.now()
    val deadline = RoundTiming.lateSubmissionDeadline(state.record.endsAt, config.lateSubmissionTolerance)
    if (now.isBefore(state.record.startsAt) || now.isAfter(deadline)) {
      return null
    }

    return when (val outcome = state.submit(playerId, path, lexicon, now)) {
      is SubmitOutcome.Accepted ->
        ServerMessage.WordAccepted(
          outcome.display,
          outcome.score,
          outcome.runningScore,
          outcome.runningWords,
          outcome.path
        )
      is SubmitOutcome.Rejected -> ServerMessage.WordRejected(outcome.reason, outcome.path)
    }
  }

  /**
   * What a `JoinRoom` gets: `RoundStart` (fresh, reconnecting, or late-joining participant) or
   * `LobbyState`. A player who is not yet a participant of the active round joins it immediately,
   * with the round's real `startsAt`/`endsAt` and no words found, unless less than
   * [ServerConfig.lateJoinMinRemaining] is left (ADR 0010: late join), in which case they keep the
   * old behavior of waiting for the next round.
   */
  @Suppress("ReturnCount") // guard-clause style: each early return is one distinct outcome (ADR 0010).
  suspend fun join(playerId: String): JoinResult {
    val state = currentRoundState
    if (state != null && state.isParticipant(playerId)) {
      val snapshot = state.playerState(playerId).snapshot()
      val alreadyFound = snapshot.found.map { WireFoundWord(it.display, it.score, it.path) }
      return JoinResult.Started(
        state.record.toRoundStartMessage(
          state.generated.solution,
          alreadyFound,
          snapshot.runningScore,
          snapshot.runningWords,
        )
      )
    }
    if (state != null) {
      val now = clock.now()
      if (RoundTiming.canLateJoin(now, state.record.endsAt, config.lateJoinMinRemaining)) {
        state.markParticipant(playerId, maxOf(state.record.startsAt, now))
        return JoinResult.Started(state.record.toRoundStartMessage(state.generated.solution))
      }
    }
    return JoinResult.Waiting(
      ServerMessage.LobbyState(nextRoundStartsAt.toEpochMilli(), connectionRegistry.connectedPlayerCount())
    )
  }

  private suspend fun waitUntil(instant: Instant) {
    val millis = Duration.between(clock.now(), instant).toMillis()
    if (millis > 0) delay(millis)
  }

  private suspend fun activate(generated: GeneratedRound): RoundState {
    val wasAlreadyActive = generated.record.status == RoundStatus.Active
    roundRepository.updateStatus(generated.record.id, RoundStatus.Active)

    val state = RoundState(generated) { accepted -> submissionRepository.insert(accepted) }
    val connectedIds = connectionRegistry.connectedPlayerIds()
    // Present when the round starts: entry time is the round's own startsAt (ADR 0010).
    connectedIds.forEach { state.markParticipant(it, generated.record.startsAt) }

    // Restart recovery (dossier phase 3 task: "persist as you go so a restart does not lose
    // accepted words"): every connection died with the old process, but the words are still in
    // `submissions`, so a reconnecting participant's `join()` needs them back in memory. Their true
    // entry time (possibly a late join, ADR 0010) is not recoverable from `submissions` alone, so
    // this approximates it as the round's own startsAt; accepted debt, same shape as ADR 0007's
    // finalization-crash gap, documented in RoundState.
    if (wasAlreadyActive) {
      submissionRepository.findByRound(generated.record.id).forEach { (playerId, words) ->
        state.markParticipant(playerId, generated.record.startsAt)
        state.playerState(playerId).restore(words)
      }
    }

    currentRoundState = state
    connectionRegistry.broadcastTo(connectedIds, generated.record.toRoundStartMessage(generated.solution))
    return state
  }

  private suspend fun finish(state: RoundState) {
    val record = state.record
    roundRepository.updateStatus(record.id, RoundStatus.Finished)

    val participantIds = state.participantSnapshot()
    if (participantIds.isEmpty()) return

    val perPlayerFound = participantIds.associateWith { playerId -> state.playerState(playerId).snapshot().found }
    val perPlayerEnteredAt = participantIds.associateWith { state.entryTimeOf(it) ?: record.startsAt }
    val result = roundFinalizer.finalize(record.id, perPlayerFound, perPlayerEnteredAt)
    val leaderboardRows = result.outcomes
      .sortedBy { it.rank }
      .take(config.leaderboardSize)
      .map { LeaderboardRow(it.rank, it.displayName, it.stats.points, it.stats.words) }

    result.outcomes.forEach { outcome ->
      val foundSet = perPlayerFound.getValue(outcome.playerId).map { it.normalized }.toSet()
      val words = state.generated.solution
        .map { solved ->
          LabelledWord(
            solved.display,
            solved.score,
            solved.tier,
            solved.path,
            solved.normalized in foundSet
          )
        }
        .sortedByDescending { it.score }
      connectionRegistry.sendTo(outcome.playerId, ServerMessage.RoundEnd(record.id, outcome.stats, words))

      val selfRow = LeaderboardRow(outcome.rank, outcome.displayName, outcome.stats.points, outcome.stats.words)
      val percentile = Percentile.of(outcome.rank, result.totalPlayers)
      connectionRegistry.sendTo(
        outcome.playerId,
        ServerMessage.Leaderboard(leaderboardRows, selfRow, percentile, result.totalPlayers),
      )
    }
  }
}

/**
 * [solution] is required, not defaulted: every caller has a [GeneratedRound] (or a [RoundState],
 * which carries one) at hand, and ADR 0014 wants `validWords` on every `RoundStart` without a
 * call site accidentally sending the empty-list fallback meant for old servers only.
 */
private fun RoundRecord.toRoundStartMessage(
  solution: List<SolvedWord>,
  alreadyFound: List<WireFoundWord> = emptyList(),
  runningScore: Int = 0,
  runningWords: Int = 0,
): ServerMessage.RoundStart = ServerMessage.RoundStart(
  roundId = id,
  board = board.tiles,
  mutator = mutator,
  themeTitle = themeTitle,
  themeSubtitle = themeSubtitle,
  maxScore = maxScore,
  maxWords = maxWords,
  startsAt = startsAt.toEpochMilli(),
  endsAt = endsAt.toEpochMilli(),
  alreadyFound = alreadyFound,
  runningScore = runningScore,
  runningWords = runningWords,
  validWords = solution.map { ValidWord(it.normalized, it.display) },
)
