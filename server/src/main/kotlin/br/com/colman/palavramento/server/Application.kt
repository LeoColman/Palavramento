// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server

import br.com.colman.palavramento.domain.lexicon.Lexicon
import br.com.colman.palavramento.server.config.ServerConfig
import br.com.colman.palavramento.server.db.DatabaseFactory
import br.com.colman.palavramento.server.lexicon.LexiconLoader
import br.com.colman.palavramento.server.plugins.appModule
import br.com.colman.palavramento.server.plugins.configureLogging
import br.com.colman.palavramento.server.plugins.configureRouting
import br.com.colman.palavramento.server.plugins.configureSecurity
import br.com.colman.palavramento.server.plugins.configureSerialization
import br.com.colman.palavramento.server.plugins.configureSockets
import br.com.colman.palavramento.server.plugins.configureStatusPages
import br.com.colman.palavramento.server.round.GameClock
import br.com.colman.palavramento.server.round.GlobalRoomId
import br.com.colman.palavramento.server.round.RoomScheduler
import br.com.colman.palavramento.server.round.SystemGameClock
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.KoinIsolated
import org.koin.logger.slf4jLogger
import kotlin.random.Random

fun main() {
  val config = ServerConfig.fromEnv()
  val database = DatabaseFactory.connect(config)
  val lexicon = LexiconLoader.load()
  embeddedServer(Netty, port = config.port, module = { module(config, database, lexicon) }).start(wait = true)
}

/**
 * Wires the whole server (dossier phase 3): Koin DI, every plugin, every route, and starts
 * [br.com.colman.palavramento.server.round.RoomScheduler]'s round loop.
 *
 * A parameter overload (rather than only a zero-arg `Application.module()`) so integration tests can
 * pass a Testcontainers-backed [database], short round/intermission [ServerConfig] durations and a
 * deterministic seed source, while production's [main] always uses real infrastructure.
 */
@Suppress("LongParameterList") // each parameter is an independent, already-documented test seam; see kdoc above.
fun Application.module(
  config: ServerConfig,
  database: Database,
  lexicon: Lexicon,
  clock: GameClock = SystemGameClock,
  seedSource: () -> Long = { Random.nextLong() },
  roomId: String = GlobalRoomId,
) {
  install(KoinIsolated) {
    slf4jLogger()
    modules(appModule(config, database, lexicon, clock, seedSource, roomId))
  }

  configureSerialization()
  configureSockets()
  configureSecurity(get())
  configureStatusPages()
  configureLogging()
  configureRouting()

  val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  get<RoomScheduler>().start(applicationScope)
  monitor.subscribe(ApplicationStopping) { applicationScope.cancel() }
}
