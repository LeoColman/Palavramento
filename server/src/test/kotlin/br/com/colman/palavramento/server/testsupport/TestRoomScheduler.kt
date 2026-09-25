// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.testsupport

import br.com.colman.palavramento.domain.lexicon.Lexicon
import br.com.colman.palavramento.server.config.ServerConfig
import br.com.colman.palavramento.server.repository.PlayerRepository
import br.com.colman.palavramento.server.repository.PlayerStatsRepository
import br.com.colman.palavramento.server.repository.RoundRepository
import br.com.colman.palavramento.server.repository.RoundResultRepository
import br.com.colman.palavramento.server.repository.SubmissionRepository
import br.com.colman.palavramento.server.round.GameClock
import br.com.colman.palavramento.server.round.RoomScheduler
import br.com.colman.palavramento.server.round.RoundFinalizer
import br.com.colman.palavramento.server.round.RoundGenerationService
import br.com.colman.palavramento.server.ws.ConnectionRegistry
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Wires a [RoomScheduler] the same way [br.com.colman.palavramento.server.plugins.appModule] does,
 * but as a plain function tests can call directly, without Koin or a running Ktor
 * [io.ktor.server.application.Application].
 */
@Suppress("LongParameterList") // each parameter is an independent test seam; mirrors appModule's own wiring.
fun buildTestScheduler(
  database: Database,
  clock: GameClock,
  config: ServerConfig,
  roomId: String = testRoomId(),
  lexicon: Lexicon = TestLexicon.lexicon,
  seedSource: () -> Long = { Random.nextLong() },
  failureBackoff: Duration = 5.seconds,
): RoomScheduler {
  val roundRepository = RoundRepository(database)
  val submissionRepository = SubmissionRepository(database)
  val playerRepository = PlayerRepository(database)
  val roundResultRepository = RoundResultRepository(database)
  val playerStatsRepository = PlayerStatsRepository(database)
  val roundGenerationService = RoundGenerationService(lexicon, config, roundRepository, seedSource)
  val roundFinalizer = RoundFinalizer(playerRepository, roundResultRepository, playerStatsRepository)
  return RoomScheduler(
    config = config,
    clock = clock,
    roundRepository = roundRepository,
    roundGenerationService = roundGenerationService,
    submissionRepository = submissionRepository,
    roundFinalizer = roundFinalizer,
    lexicon = lexicon,
    connectionRegistry = ConnectionRegistry(),
    roomId = roomId,
    failureBackoff = failureBackoff,
  )
}
