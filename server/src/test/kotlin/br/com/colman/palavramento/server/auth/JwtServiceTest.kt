// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.auth

import br.com.colman.palavramento.server.config.ServerConfig
import br.com.colman.palavramento.server.round.MutableGameClock
import com.auth0.jwt.JWT
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.minutes

private fun config(
  secret: String = "test-secret",
  issuer: String = "test-issuer",
  audience: String = "test-audience",
) = ServerConfig(jwtSecret = secret, jwtIssuer = issuer, jwtAudience = audience, accessTokenTtl = 15.minutes)

/**
 * The JWT library checks `exp` against the real system clock, not against the injected game clock,
 * so tokens that are meant to verify are issued from a clock reading wall-clock time. Only the
 * arithmetic test pins an instant, and it never verifies.
 */
private fun wallClock(offsetSeconds: Long = 0) =
  MutableGameClock(Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(offsetSeconds))

class JwtServiceTest : FunSpec({
  test("A freshly issued token verifies and carries back its player id and guest flag") {
    val service = JwtService(config(), wallClock())

    val signed = service.createAccessToken("player-1", isGuest = true)
    val verified = service.verifyAccessToken(signed.token)

    verified.shouldNotBeNull()
    verified.playerId shouldBe "player-1"
    verified.isGuest shouldBe true
  }

  test("A registered player's token carries isGuest false") {
    val service = JwtService(config(), wallClock())

    val signed = service.createAccessToken("player-2", isGuest = false)

    service.verifyAccessToken(signed.token)?.isGuest shouldBe false
  }

  test("expiresAt is the clock's now plus the configured TTL, and it is what the token says") {
    val issuedAt = Instant.parse("2026-01-01T00:00:00Z")
    val service = JwtService(config(), MutableGameClock(issuedAt))

    val signed = service.createAccessToken("player-1", isGuest = false)

    signed.expiresAt shouldBe issuedAt.plusSeconds(15 * 60)
    JWT.decode(signed.token).issuedAtAsInstant shouldBe issuedAt
    JWT.decode(signed.token).expiresAtAsInstant shouldBe signed.expiresAt
    JWT.decode(signed.token).subject shouldBe "player-1"
  }

  test("A token whose TTL already elapsed no longer verifies") {
    // Issued an hour ago with a 15 minute TTL: expired by the time the verifier looks at it.
    val service = JwtService(config(), wallClock(offsetSeconds = -3600))

    val signed = service.createAccessToken("player-1", isGuest = false)

    service.verifyAccessToken(signed.token).shouldBeNull()
  }

  test("A token signed with another secret does not verify") {
    val issuer = JwtService(config(secret = "one-secret"), wallClock())
    val verifier = JwtService(config(secret = "another-secret"), wallClock())

    val signed = issuer.createAccessToken("player-1", isGuest = false)

    verifier.verifyAccessToken(signed.token).shouldBeNull()
  }

  test("A token from another issuer or another audience does not verify") {
    val ours = JwtService(config(), wallClock())
    val otherIssuer = JwtService(config(issuer = "someone-else"), wallClock())
    val otherAudience = JwtService(config(audience = "someone-else"), wallClock())

    ours.verifyAccessToken(otherIssuer.createAccessToken("p", isGuest = false).token).shouldBeNull()
    ours.verifyAccessToken(otherAudience.createAccessToken("p", isGuest = false).token).shouldBeNull()
  }

  test("Garbage is rejected instead of throwing") {
    val service = JwtService(config(), wallClock())

    service.verifyAccessToken("").shouldBeNull()
    service.verifyAccessToken("not-a-jwt").shouldBeNull()
    service.verifyAccessToken("a.b.c").shouldBeNull()
  }
})
