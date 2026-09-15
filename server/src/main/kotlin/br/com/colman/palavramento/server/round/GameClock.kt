// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.round

import java.time.Instant

/**
 * The server's single source of time for round scheduling, clock sync and submission timing
 * (dossier 5.3: "o relogio... nunca usa o relogio local"). An interface so tests can inject a
 * [MutableGameClock] instead of waiting on wall-clock time for timing-math assertions.
 */
fun interface GameClock {
  fun now(): Instant
}

/** Production clock: the JVM's own wall-clock time. */
object SystemGameClock : GameClock {
  override fun now(): Instant = Instant.now()
}

/** Test clock: starts at [initial] and only moves when [advance] or [set] is called. */
class MutableGameClock(initial: Instant = Instant.EPOCH) : GameClock {
  @Volatile
  private var instant: Instant = initial

  override fun now(): Instant = instant

  fun set(value: Instant) {
    instant = value
  }

  fun advance(millis: Long) {
    instant = instant.plusMillis(millis)
  }
}
