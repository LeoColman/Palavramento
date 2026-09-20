// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.config

import br.com.colman.palavramento.domain.generator.GenerationCriteria
import br.com.colman.palavramento.domain.solver.Solver
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Every tunable the server needs (dossier phase 3). Tunables the orchestrator is calibrating in
 * `:domain` (letter values, common/expert cutoff, generation criteria) are never hardcoded here:
 * they default from `:domain` ([Solver.DefaultCommonCutoff], [GenerationCriteria]'s own defaults,
 * `LetterValueTable.default`, `LetterWeightTable.default` used directly by [BoardGenerator]) and are
 * only overridden through environment variables, so a redeploy of `:domain` alone changes behavior
 * without a server code change.
 *
 * Loaded once at startup from environment variables with sane local defaults so `./gradlew
 * :server:run` works against the docker-compose Postgres without any extra configuration. Tests
 * build their own [ServerConfig] directly (e.g. short [roundDuration]/[intermissionDuration]) rather
 * than going through [fromEnv].
 */
data class ServerConfig(
  val port: Int = DefaultPort,
  val databaseUrl: String = "jdbc:postgresql://localhost:5432/palavramento",
  val databaseUser: String = "palavramento",
  val databasePassword: String = "palavramento",
  val jwtSecret: String = "dev-secret-change-me-in-production",
  val jwtIssuer: String = "palavramento-server",
  val jwtAudience: String = "palavramento-clients",
  val accessTokenTtl: Duration = 15.minutes,
  val refreshTokenTtl: Duration = 30.days,
  val roundDuration: Duration = 120.seconds,
  // 25 s, not the dossier's 60 s (§1.4): the owner shortened the results break on 2026-09-15.
  val intermissionDuration: Duration = 25.seconds,
  val lateSubmissionTolerance: Duration = 500.milliseconds,
  val submitRateLimitPerSecond: Int = DefaultRateLimit,
  val commonCutoff: Int = Solver.DefaultCommonCutoff,
  val generationCriteria: GenerationCriteria = GenerationCriteria(),
  val leaderboardSize: Int = DefaultLeaderboardSize,
  // Late join (ADR 0010, product decision 2026-09-15): below this much time left in the active
  // round, a not-yet-participant keeps the old behavior (LobbyState, wait for the next round)
  // instead of being let into the round already running.
  val lateJoinMinRemaining: Duration = DefaultLateJoinMinRemainingSeconds.seconds,
  // Metrics (ADR 0019). Null token leaves /metrics off: nothing to scrape, nothing to guess.
  val metricsToken: String? = null,
  val metricsRefreshInterval: Duration = DefaultMetricsRefreshSeconds.seconds,
) {
  companion object {
    private const val DefaultPort = 8080
    private const val DefaultRateLimit = 10
    private const val DefaultLeaderboardSize = 20
    private const val DefaultLateJoinMinRemainingSeconds = 10
    private const val DefaultMetricsRefreshSeconds = 60

    /** Reads every variable below, falling back to the [ServerConfig] default when unset. */
    fun fromEnv(env: (String) -> String? = System::getenv): ServerConfig {
      val defaults = ServerConfig()
      return ServerConfig(
        port = envInt(env, "PORT", defaults.port),
        databaseUrl = env("DATABASE_URL") ?: defaults.databaseUrl,
        databaseUser = env("DATABASE_USER") ?: defaults.databaseUser,
        databasePassword = env("DATABASE_PASSWORD") ?: defaults.databasePassword,
        jwtSecret = env("JWT_SECRET") ?: defaults.jwtSecret,
        jwtIssuer = env("JWT_ISSUER") ?: defaults.jwtIssuer,
        jwtAudience = env("JWT_AUDIENCE") ?: defaults.jwtAudience,
        accessTokenTtl = envSeconds(env, "ACCESS_TOKEN_TTL_SECONDS", defaults.accessTokenTtl),
        refreshTokenTtl = envSeconds(env, "REFRESH_TOKEN_TTL_SECONDS", defaults.refreshTokenTtl),
        roundDuration = envSeconds(env, "ROUND_DURATION_SECONDS", defaults.roundDuration),
        intermissionDuration = envSeconds(env, "INTERMISSION_DURATION_SECONDS", defaults.intermissionDuration),
        lateSubmissionTolerance = envMillis(env, "LATE_SUBMISSION_TOLERANCE_MILLIS", defaults.lateSubmissionTolerance),
        submitRateLimitPerSecond = envInt(env, "SUBMIT_RATE_LIMIT_PER_SECOND", defaults.submitRateLimitPerSecond),
        commonCutoff = envInt(env, "COMMON_CUTOFF", defaults.commonCutoff),
        generationCriteria = defaults.generationCriteria,
        leaderboardSize = envInt(env, "LEADERBOARD_SIZE", defaults.leaderboardSize),
        lateJoinMinRemaining = envSeconds(env, "LATE_JOIN_MIN_REMAINING_SECONDS", defaults.lateJoinMinRemaining),
        metricsToken = env("METRICS_TOKEN")?.takeIf { it.isNotBlank() },
        metricsRefreshInterval = envSeconds(env, "METRICS_REFRESH_SECONDS", defaults.metricsRefreshInterval),
      )
    }

    private fun envInt(env: (String) -> String?, key: String, default: Int): Int =
      env(key)?.toIntOrNull() ?: default

    private fun envSeconds(env: (String) -> String?, key: String, default: Duration): Duration =
      env(key)?.toLongOrNull()?.seconds ?: default

    private fun envMillis(env: (String) -> String?, key: String, default: Duration): Duration =
      env(key)?.toLongOrNull()?.milliseconds ?: default
  }
}
