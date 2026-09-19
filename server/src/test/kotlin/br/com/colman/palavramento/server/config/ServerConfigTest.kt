// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.config

import br.com.colman.palavramento.domain.generator.GenerationCriteria
import br.com.colman.palavramento.domain.solver.Solver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * [ServerConfig.fromEnv] takes its lookup as a parameter precisely so this spec never touches the
 * real environment. Every property is read one by one rather than comparing two [ServerConfig]
 * instances: a getter only counts as exercised when a test actually reads it.
 */
class ServerConfigTest : FunSpec({
  test("With nothing set, every value falls back to the declared default") {
    val config = ServerConfig.fromEnv { null }

    config.port shouldBe 8080
    config.databaseUrl shouldBe "jdbc:postgresql://localhost:5432/palavramento"
    config.databaseUser shouldBe "palavramento"
    config.databasePassword shouldBe "palavramento"
    config.jwtSecret shouldBe "dev-secret-change-me-in-production"
    config.jwtIssuer shouldBe "palavramento-server"
    config.jwtAudience shouldBe "palavramento-clients"
    config.accessTokenTtl shouldBe 15.minutes
    config.refreshTokenTtl shouldBe 30.days
    config.roundDuration shouldBe 120.seconds
    // 25 s, not the dossier's 60 s: the owner shortened the results break on 2026-09-15.
    config.intermissionDuration shouldBe 25.seconds
    config.lateSubmissionTolerance shouldBe 500.milliseconds
    config.submitRateLimitPerSecond shouldBe 10
    config.commonCutoff shouldBe Solver.DefaultCommonCutoff
    config.generationCriteria shouldBe GenerationCriteria()
    config.leaderboardSize shouldBe 20
    config.lateJoinMinRemaining shouldBe 10.seconds
  }

  test("Every variable is read from its own key") {
    val env = mapOf(
      "PORT" to "9001",
      "DATABASE_URL" to "jdbc:postgresql://db:5432/other",
      "DATABASE_USER" to "someone",
      "DATABASE_PASSWORD" to "s3cret",
      "JWT_SECRET" to "another-secret",
      "JWT_ISSUER" to "issuer",
      "JWT_AUDIENCE" to "audience",
      "ACCESS_TOKEN_TTL_SECONDS" to "60",
      "REFRESH_TOKEN_TTL_SECONDS" to "120",
      "ROUND_DURATION_SECONDS" to "30",
      "INTERMISSION_DURATION_SECONDS" to "5",
      "LATE_SUBMISSION_TOLERANCE_MILLIS" to "250",
      "SUBMIT_RATE_LIMIT_PER_SECOND" to "3",
      "COMMON_CUTOFF" to "7",
      "LEADERBOARD_SIZE" to "50",
      "LATE_JOIN_MIN_REMAINING_SECONDS" to "42",
    )

    val config = ServerConfig.fromEnv(env::get)

    config.port shouldBe 9001
    config.databaseUrl shouldBe "jdbc:postgresql://db:5432/other"
    config.databaseUser shouldBe "someone"
    config.databasePassword shouldBe "s3cret"
    config.jwtSecret shouldBe "another-secret"
    config.jwtIssuer shouldBe "issuer"
    config.jwtAudience shouldBe "audience"
    config.accessTokenTtl shouldBe 60.seconds
    config.refreshTokenTtl shouldBe 120.seconds
    config.roundDuration shouldBe 30.seconds
    config.intermissionDuration shouldBe 5.seconds
    config.lateSubmissionTolerance shouldBe 250.milliseconds
    config.submitRateLimitPerSecond shouldBe 3
    config.commonCutoff shouldBe 7
    config.leaderboardSize shouldBe 50
    config.lateJoinMinRemaining shouldBe 42.seconds
  }

  test("A value that is not a number falls back to the default instead of failing startup") {
    val env = mapOf(
      "PORT" to "http",
      "ROUND_DURATION_SECONDS" to "two minutes",
      "LATE_SUBMISSION_TOLERANCE_MILLIS" to "",
    )

    val config = ServerConfig.fromEnv(env::get)

    config.port shouldBe 8080
    config.roundDuration shouldBe 120.seconds
    config.lateSubmissionTolerance shouldBe 500.milliseconds
  }

  test("Generation criteria stay on the :domain defaults, never read from the environment") {
    val env = mapOf("GENERATION_CRITERIA" to "ignored")

    ServerConfig.fromEnv(env::get).generationCriteria shouldBe GenerationCriteria()
  }
})
