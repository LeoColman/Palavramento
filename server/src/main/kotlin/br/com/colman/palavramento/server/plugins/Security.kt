// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.plugins

import br.com.colman.palavramento.server.auth.JwtService
import br.com.colman.palavramento.server.auth.VerifiedAccessToken
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond

/** JWT bearer auth for REST (dossier §8): `/players/me*` require it, `/auth/register|login` accept it optionally. */
fun Application.configureSecurity(jwtService: JwtService) {
  install(Authentication) {
    jwt(JwtProviderName) {
      verifier(jwtService.verifier)
      validate { credential -> if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null }
      challenge { _, _ -> call.respond(HttpStatusCode.Unauthorized) }
    }
  }
}

const val JwtProviderName = "jwt"

/** Reads the verified claims a [JWTPrincipal] carries, matching [JwtService.verifyAccessToken]'s shape. */
fun JWTPrincipal.toVerifiedAccessToken(): VerifiedAccessToken = VerifiedAccessToken(
  playerId = requireNotNull(payload.subject) { "JWTPrincipal without a subject should never validate" },
  isGuest = payload.getClaim(JwtService.GuestClaim).asBoolean() ?: false,
)
