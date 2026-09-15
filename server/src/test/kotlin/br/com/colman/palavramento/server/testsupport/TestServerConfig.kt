// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.testsupport

import br.com.colman.palavramento.server.config.ServerConfig
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A [ServerConfig] with short round/intermission durations (dossier phase 3 task: "tests override
 * durations, e.g. 3s rounds, 2s intermission"), otherwise identical to production defaults, so a
 * full round cycle test finishes in a few seconds of real wall-clock time.
 */
fun testServerConfig(
  roundDuration: kotlin.time.Duration = 3.seconds,
  intermissionDuration: kotlin.time.Duration = 2.seconds,
  lateJoinMinRemaining: kotlin.time.Duration = 10.seconds,
): ServerConfig = ServerConfig(
  roundDuration = roundDuration,
  intermissionDuration = intermissionDuration,
  lateSubmissionTolerance = 500.milliseconds,
  jwtSecret = "test-secret-key-not-for-production-use",
  accessTokenTtl = 1.hours,
  refreshTokenTtl = 1.days,
  submitRateLimitPerSecond = 10,
  leaderboardSize = 10,
  lateJoinMinRemaining = lateJoinMinRemaining,
)
