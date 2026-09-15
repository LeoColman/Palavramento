// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.network

/**
 * Exponential backoff, capped, for `/ws/multiplayer` reconnection attempts (task brief: "Reconnect
 * with backoff if the socket drops"). Pure and deterministic so retry timing is unit-testable
 * without waiting on a real clock; [MultiplayerSession] is the only caller.
 */
class ReconnectBackoff(
  private val baseDelayMs: Long = DefaultBaseDelayMs,
  private val maxDelayMs: Long = DefaultMaxDelayMs,
) {

  /** Delay before reconnect attempt number [attempt] (0-based: the first retry is attempt 0). */
  fun delayForAttempt(attempt: Int): Long {
    require(attempt >= 0) { "Attempt must not be negative, got $attempt" }
    val scaled = baseDelayMs shl attempt.coerceAtMost(MaxShift)
    return scaled.coerceAtMost(maxDelayMs)
  }

  private companion object {
    const val DefaultBaseDelayMs = 1_000L
    const val DefaultMaxDelayMs = 30_000L

    // Caps the left shift so a long streak of failures cannot overflow into a negative Long instead
    // of saturating at maxDelayMs.
    const val MaxShift = 32
  }
}
