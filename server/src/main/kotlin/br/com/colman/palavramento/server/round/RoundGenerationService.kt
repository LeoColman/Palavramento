// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.round

import br.com.colman.palavramento.domain.generator.BoardGenerator
import br.com.colman.palavramento.domain.generator.RoundDescriptorPicker
import br.com.colman.palavramento.domain.lexicon.Lexicon
import br.com.colman.palavramento.domain.mutator.MutatorTheme
import br.com.colman.palavramento.domain.solver.Solver
import br.com.colman.palavramento.server.config.ServerConfig
import br.com.colman.palavramento.server.repository.RoundRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/**
 * Builds and persists rounds ahead of time (dossier §3: "pre-geradas com pelo menos uma de
 * antecedencia"), off the request/scheduler-tick path: [generateAndPersist] hops to
 * [Dispatchers.Default] for the actual solve, since a board solve walks the whole lexicon trie and
 * should never block the scheduler's own coroutine (which also drives round start/end broadcasts).
 *
 * [seedSource] defaults to a random seed per round; tests inject a deterministic sequence so they
 * can pick words to submit straight from the round's persisted solution (dossier §3: same seed,
 * same grid, required for reproducibility and for tests).
 */
class RoundGenerationService(
  lexicon: Lexicon,
  private val config: ServerConfig,
  private val roundRepository: RoundRepository,
  private val seedSource: () -> Long = { Random.nextLong() },
) {
  private val solver = Solver(lexicon)
  private val boardGenerator = BoardGenerator(solver)

  suspend fun generateAndPersist(roomId: String, startsAt: Instant, endsAt: Instant): GeneratedRound {
    val seed = seedSource()
    val descriptor = RoundDescriptorPicker.pick(seed)
    val generation = withContext(Dispatchers.Default) {
      boardGenerator.generate(
        seed = seed,
        mutator = descriptor.mutator,
        commonCutoff = config.commonCutoff,
        criteria = config.generationCriteria,
      )
    }
    val record = RoundRecord(
      id = UUID.randomUUID().toString(),
      roomId = roomId,
      seed = seed,
      board = generation.board,
      mutator = descriptor.mutator,
      themeTitle = MutatorTheme.title(descriptor.mutator),
      themeSubtitle = MutatorTheme.subtitle(descriptor.commonMin),
      commonMin = descriptor.commonMin,
      maxScore = generation.solution.sumOf { it.score },
      maxWords = generation.solution.size,
      startsAt = startsAt,
      endsAt = endsAt,
      status = RoundStatus.Scheduled,
    )
    roundRepository.insert(record, generation.solution)
    return GeneratedRound(record, generation.solution)
  }

  /**
   * The round [RoomScheduler] should treat as "current" for [roomId]: the oldest not-yet-finished
   * one already persisted (restart recovery, dossier phase 3 task), or a freshly generated one
   * starting one intermission after [now] when none exists yet.
   */
  suspend fun recoverOrGenerateCurrent(roomId: String, now: Instant): GeneratedRound {
    val pending = roundRepository.findPending(roomId, limit = PendingLookahead)
    val record = pending.getOrNull(0) ?: return generateFirst(roomId, now)
    return GeneratedRound(record, roundRepository.loadSolution(record.id))
  }

  /** The round after [current]: already persisted (restart recovery) or freshly generated. */
  suspend fun recoverOrGenerateNext(roomId: String, current: GeneratedRound): GeneratedRound {
    val pending = roundRepository.findPending(roomId, limit = PendingLookahead)
    val record = pending.getOrNull(1)
    return if (record != null && record.id != current.record.id) {
      GeneratedRound(record, roundRepository.loadSolution(record.id))
    } else {
      generateNext(current)
    }
  }

  /** Generates and persists the round following [after], one intermission after it ends. */
  suspend fun generateNext(after: GeneratedRound): GeneratedRound {
    val startsAt = RoundTiming.nextRoundStartsAt(after.record.endsAt, config.intermissionDuration)
    val endsAt = RoundTiming.endsAt(startsAt, config.roundDuration)
    return generateAndPersist(after.record.roomId, startsAt, endsAt)
  }

  private suspend fun generateFirst(roomId: String, now: Instant): GeneratedRound {
    val startsAt = RoundTiming.firstRoundStartsAt(now, config.intermissionDuration)
    val endsAt = RoundTiming.endsAt(startsAt, config.roundDuration)
    return generateAndPersist(roomId, startsAt, endsAt)
  }

  private companion object {
    const val PendingLookahead = 2
  }
}
