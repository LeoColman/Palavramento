// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.auth

import br.com.colman.palavramento.server.config.ServerConfig
import br.com.colman.palavramento.server.round.GameClock
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.Date

/** A short-lived access token, signed and ready to hand to a client (dossier §8). */
data class SignedAccessToken(val token: String, val expiresAt: Instant)

/** The claims a valid access token carried. */
data class VerifiedAccessToken(val playerId: String, val isGuest: Boolean)

/**
 * Issues and verifies HS256 JWT access tokens (dossier §8: "JWT de curta duracao"). TLS is
 * terminated by the deployment (ADR 0007), not by this class.
 */
class JwtService(private val config: ServerConfig, private val clock: GameClock) {
  private val algorithm: Algorithm = Algorithm.HMAC256(config.jwtSecret)

  val verifier: JWTVerifier = JWT.require(algorithm)
    .withIssuer(config.jwtIssuer)
    .withAudience(config.jwtAudience)
    .build()

  fun createAccessToken(playerId: String, isGuest: Boolean): SignedAccessToken {
    val now = clock.now()
    val expiresAt = now.plusMillis(config.accessTokenTtl.inWholeMilliseconds)
    val token = JWT.create()
      .withIssuer(config.jwtIssuer)
      .withAudience(config.jwtAudience)
      .withSubject(playerId)
      .withClaim(GuestClaim, isGuest)
      .withIssuedAt(Date.from(now))
      .withExpiresAt(Date.from(expiresAt))
      .sign(algorithm)
    return SignedAccessToken(token, expiresAt)
  }

  /** Verifies [token], returning null for anything invalid, expired or wrongly signed. */
  fun verifyAccessToken(token: String): VerifiedAccessToken? = try {
    val decoded = verifier.verify(token)
    VerifiedAccessToken(playerId = decoded.subject, isGuest = decoded.getClaim(GuestClaim).asBoolean() ?: false)
  } catch (cause: JWTVerificationException) {
    logger.debug("Rejected an invalid access token: {}", cause.message)
    null
  }

  companion object {
    const val GuestClaim = "guest"
    private val logger = LoggerFactory.getLogger(JwtService::class.java)
  }
}
