// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.rest

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `GET /privacidade` and `GET /exclusao-de-conta` (ADR 0020): the two web pages the Play Store
 * requires from any app that lets someone create an account. Static HTML, no template engine, no
 * authentication; each page is read once from the classpath and reused for every request, since
 * the content never changes per visitor.
 */
fun Route.legalRoutes() {
  get("/privacidade") { call.respondText(LegalPages.privacyPolicy, ContentType.Text.Html) }
  get("/exclusao-de-conta") { call.respondText(LegalPages.accountDeletion, ContentType.Text.Html) }
}

private object LegalPages {
  val privacyPolicy = load("/privacidade.html")
  val accountDeletion = load("/exclusao-de-conta.html")

  private fun load(resourcePath: String): String {
    val stream = checkNotNull(javaClass.getResourceAsStream(resourcePath)) {
      "Missing classpath resource $resourcePath"
    }
    return stream.use { it.readBytes().toString(Charsets.UTF_8) }
  }
}
