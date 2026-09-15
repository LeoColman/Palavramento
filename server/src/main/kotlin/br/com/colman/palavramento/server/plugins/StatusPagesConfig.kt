// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("StatusPages")

/** Maps uncaught exceptions to HTTP responses instead of a bare 500 with a stack trace on the wire. */
fun Application.configureStatusPages() {
  install(StatusPages) {
    exception<IllegalArgumentException> { call, cause ->
      call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Bad request")))
    }
    exception<Throwable> { call, cause ->
      logger.error("Unhandled exception", cause)
      call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
    }
  }
}
