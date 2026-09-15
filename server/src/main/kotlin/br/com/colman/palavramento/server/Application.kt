// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

private const val DefaultPort = 8080

fun main() {
  embeddedServer(Netty, port = DefaultPort, module = Application::module).start(wait = true)
}

fun Application.module() {
  routing {
    get("/health") { call.respondText("ok") }
  }
}
