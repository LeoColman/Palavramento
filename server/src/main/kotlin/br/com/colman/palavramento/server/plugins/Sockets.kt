// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlin.time.Duration.Companion.seconds

private val PingPeriod = 15.seconds
private val Timeout = 30.seconds

/** WebSocket support for `/ws/multiplayer` (dossier §5). */
fun Application.configureSockets() {
  install(WebSockets) {
    pingPeriod = PingPeriod
    timeout = Timeout
  }
}
