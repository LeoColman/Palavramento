// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import org.slf4j.event.Level

/** Request/response logging for every HTTP call (WebSocket upgrades included). */
fun Application.configureLogging() {
  install(CallLogging) {
    level = Level.INFO
  }
}
