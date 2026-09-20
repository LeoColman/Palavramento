// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.rest

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.security.MessageDigest

private const val BearerPrefix = "Bearer "

/**
 * `GET /metrics` in Prometheus text format (ADR 0019), for the Prometheus that already runs on the
 * home network to scrape over the public address.
 *
 * [token] comes from `METRICS_TOKEN`. Unset, the route answers 404: a server nobody configured for
 * monitoring should not advertise that the endpoint exists at all. Set, the bearer token has to
 * match, because these numbers say how many people play and when, which is nobody else's business.
 */
fun Route.metricsRoutes(registry: PrometheusMeterRegistry, token: String?) {
  get("/metrics") {
    val expected = token
    if (expected == null) {
      call.respond(HttpStatusCode.NotFound)
      return@get
    }
    val offered = call.request.header(HttpHeaders.Authorization)?.takeIf { it.startsWith(BearerPrefix) }
      ?.removePrefix(BearerPrefix)
    if (!matchesToken(offered, expected)) {
      call.respond(HttpStatusCode.Unauthorized, ErrorBody("Invalid metrics token"))
      return@get
    }
    call.respondText(registry.scrape(), ContentType.Text.Plain)
  }
}

/** Compares in constant time, so a wrong token leaks nothing through how long the answer took. */
private fun matchesToken(offered: String?, expected: String): Boolean {
  if (offered == null) return false
  return MessageDigest.isEqual(offered.toByteArray(), expected.toByteArray())
}
