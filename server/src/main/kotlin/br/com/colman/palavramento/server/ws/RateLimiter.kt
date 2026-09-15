// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.ws

import br.com.colman.palavramento.server.round.GameClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fixed-window rate limiter, one per connection (dossier phase 3 task: "10 submissions/s per
 * connection, excess silently dropped and logged").
 */
class RateLimiter(private val maxPerSecond: Int, private val clock: GameClock) {
  private val mutex = Mutex()
  private var windowStartMillis = 0L
  private var countInWindow = 0

  suspend fun tryAcquire(): Boolean = mutex.withLock {
    val now = clock.now().toEpochMilli()
    if (now - windowStartMillis >= MillisPerSecond) {
      windowStartMillis = now
      countInWindow = 0
    }
    if (countInWindow >= maxPerSecond) {
      false
    } else {
      countInWindow++
      true
    }
  }

  private companion object {
    const val MillisPerSecond = 1000L
  }
}
