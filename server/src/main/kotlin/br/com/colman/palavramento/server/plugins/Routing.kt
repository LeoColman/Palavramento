// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.plugins

import br.com.colman.palavramento.server.auth.AuthService
import br.com.colman.palavramento.server.auth.JwtService
import br.com.colman.palavramento.server.config.ServerConfig
import br.com.colman.palavramento.server.repository.PlayerRepository
import br.com.colman.palavramento.server.repository.PlayerStatsRepository
import br.com.colman.palavramento.server.repository.RoundRepository
import br.com.colman.palavramento.server.repository.RoundResultRepository
import br.com.colman.palavramento.server.repository.SubmissionRepository
import br.com.colman.palavramento.server.rest.authRoutes
import br.com.colman.palavramento.server.rest.legalRoutes
import br.com.colman.palavramento.server.rest.metricsRoutes
import br.com.colman.palavramento.server.rest.playerRoutes
import br.com.colman.palavramento.server.round.GameClock
import br.com.colman.palavramento.server.round.RoomScheduler
import br.com.colman.palavramento.server.ws.multiplayerRoute
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.koin.ktor.ext.get as koinGet

/**
 * Mounts every route: `/health`, `/auth/...`, `/players/me*` and `/ws/multiplayer`. Each dependency
 * is looked up from the Koin container installed by [appModule] right here (`koinGet`), instead of
 * being threaded through as a parameter from [br.com.colman.palavramento.server.module]: the
 * alternative is one function with ten unrelated parameters, which does not become more readable by
 * being someone else's problem.
 */
fun Application.configureRouting() {
  val config = koinGet<ServerConfig>()
  val clock = koinGet<GameClock>()
  val jwtService = koinGet<JwtService>()
  val authService = koinGet<AuthService>()
  val playerRepository = koinGet<PlayerRepository>()
  val playerStatsRepository = koinGet<PlayerStatsRepository>()
  val roundResultRepository = koinGet<RoundResultRepository>()
  val roundRepository = koinGet<RoundRepository>()
  val submissionRepository = koinGet<SubmissionRepository>()
  val roomScheduler = koinGet<RoomScheduler>()
  val meterRegistry = koinGet<PrometheusMeterRegistry>()

  routing {
    get("/health") { call.respondText("ok") }
    legalRoutes()
    authRoutes(authService)
    playerRoutes(playerRepository, playerStatsRepository, roundResultRepository, roundRepository, submissionRepository)
    multiplayerRoute(roomScheduler, jwtService, config, clock)
    metricsRoutes(meterRegistry, config.metricsToken)
  }
}
