// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.rest

import br.com.colman.palavramento.domain.protocol.AuthTokens
import br.com.colman.palavramento.domain.protocol.GuestAuthRequest
import br.com.colman.palavramento.domain.protocol.LoginRequest
import br.com.colman.palavramento.domain.protocol.RefreshRequest
import br.com.colman.palavramento.domain.protocol.RegisterRequest
import br.com.colman.palavramento.server.auth.AuthService
import br.com.colman.palavramento.server.auth.LoginOutcome
import br.com.colman.palavramento.server.auth.RefreshOutcome
import br.com.colman.palavramento.server.auth.RegisterOutcome
import br.com.colman.palavramento.server.plugins.JwtProviderName
import br.com.colman.palavramento.server.plugins.toVerifiedAccessToken
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

/**
 * `/auth/...` (dossier §8, `Rest.kt`). `/auth/register` and `/auth/login` accept a guest's access
 * token optionally: when present and valid, [AuthService] promotes or migrates that guest instead
 * of creating an unrelated account (dossier §8, ADR 0007).
 */
fun Route.authRoutes(authService: AuthService) {
  post("/auth/guest") {
    val body = call.receive<GuestAuthRequest>()
    call.respond(authService.guest(body.displayName))
  }

  post("/auth/refresh") {
    val body = call.receive<RefreshRequest>()
    when (val outcome = authService.refresh(body.refreshToken)) {
      is RefreshOutcome.Success -> call.respond(outcome.tokens)
      RefreshOutcome.Invalid -> call.respond(HttpStatusCode.Unauthorized, ErrorBody("Invalid or expired refresh token"))
    }
  }

  authenticate(JwtProviderName, optional = true) {
    post("/auth/register") {
      val body = call.receive<RegisterRequest>()
      val guestPlayerId = call.principal<JWTPrincipal>()?.toVerifiedAccessToken()?.takeIf { it.isGuest }?.playerId
      when (val outcome = authService.register(guestPlayerId, body.email, body.password, body.displayName)) {
        is RegisterOutcome.Success -> call.respond(outcome.tokens)
        RegisterOutcome.EmailTaken -> call.respond(HttpStatusCode.Conflict, ErrorBody("Email already registered"))
        RegisterOutcome.GuestNotFound -> call.respond(HttpStatusCode.Unauthorized, ErrorBody("Guest session not found"))
        RegisterOutcome.NotAGuest -> call.respond(
          HttpStatusCode.Conflict,
          ErrorBody("Session does not belong to a guest")
        )
      }
    }

    post("/auth/login") {
      val body = call.receive<LoginRequest>()
      val guestPlayerId = call.principal<JWTPrincipal>()?.toVerifiedAccessToken()?.takeIf { it.isGuest }?.playerId
      when (val outcome = authService.login(guestPlayerId, body.email, body.password)) {
        is LoginOutcome.Success -> call.respond(outcome.tokens)
        LoginOutcome.InvalidCredentials -> call.respond(
          HttpStatusCode.Unauthorized,
          ErrorBody("Invalid email or password")
        )
      }
    }
  }
}

/** [AuthTokens] is never wrapped; errors are, so REST clients can tell a body apart from a message. */
@Serializable
internal data class ErrorBody(val error: String)
