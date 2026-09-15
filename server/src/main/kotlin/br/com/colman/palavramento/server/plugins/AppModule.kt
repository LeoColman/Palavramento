// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.plugins

import br.com.colman.palavramento.domain.lexicon.Lexicon
import br.com.colman.palavramento.server.auth.AuthService
import br.com.colman.palavramento.server.auth.JwtService
import br.com.colman.palavramento.server.config.ServerConfig
import br.com.colman.palavramento.server.repository.PlayerRepository
import br.com.colman.palavramento.server.repository.PlayerStatsRepository
import br.com.colman.palavramento.server.repository.RefreshTokenRepository
import br.com.colman.palavramento.server.repository.RoundRepository
import br.com.colman.palavramento.server.repository.RoundResultRepository
import br.com.colman.palavramento.server.repository.SubmissionRepository
import br.com.colman.palavramento.server.round.GameClock
import br.com.colman.palavramento.server.round.GlobalRoomId
import br.com.colman.palavramento.server.round.RoomScheduler
import br.com.colman.palavramento.server.round.RoundFinalizer
import br.com.colman.palavramento.server.round.RoundGenerationService
import br.com.colman.palavramento.server.round.SystemGameClock
import br.com.colman.palavramento.server.ws.ConnectionRegistry
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.random.Random

/**
 * Every server singleton, wired by hand instead of split across many small Koin modules: phase 3's
 * whole dependency graph (config -> lexicon/database -> repositories -> services -> the scheduler)
 * is a single straight line with no alternate implementations to swap, so one module is clearer than
 * several that would all need to be installed together anyway.
 *
 * [clock] and [seedSource] default to production behavior (real time, random seeds) but are
 * parameters so integration tests can inject short round durations (via [config]) while keeping the
 * real clock, and unit tests can inject a fake [GameClock] without touching this module at all.
 */
@Suppress("LongParameterList") // each parameter is an independent, already-documented test seam; see kdoc above.
fun appModule(
  config: ServerConfig,
  database: Database,
  lexicon: Lexicon,
  clock: GameClock = SystemGameClock,
  seedSource: () -> Long = { Random.nextLong() },
  roomId: String = GlobalRoomId,
): Module = module {
  single { config }
  single { database }
  single { lexicon }
  single { clock }

  single { PlayerRepository(get()) }
  single { RefreshTokenRepository(get()) }
  single { RoundRepository(get()) }
  single { SubmissionRepository(get()) }
  single { RoundResultRepository(get()) }
  single { PlayerStatsRepository(get()) }

  single { JwtService(get(), get()) }
  single { AuthService(get(), get(), get(), get(), get(), get(), get(), get()) }

  single { RoundGenerationService(get(), get(), get(), seedSource) }
  single { RoundFinalizer(get(), get(), get()) }
  single { ConnectionRegistry() }
  single { RoomScheduler(get(), get(), get(), get(), get(), get(), get(), get(), roomId) }
}
