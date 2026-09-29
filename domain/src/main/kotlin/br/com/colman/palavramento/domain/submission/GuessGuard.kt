// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.submission

/**
 * Discourages tracing at random until something sticks: [MaxMisses] non-words in a row lock the
 * board for [LockMs]. Only a [RejectionReason.NotAWord] counts as a miss; a repeat, an invalid path
 * or a word too short is a slip, not a guess, and leaves the count alone. Any accepted word resets
 * it. No points are ever taken (owner decision, 2026-09-29).
 *
 * Immutable: every event returns the next guard. Times are milliseconds on whatever clock the caller
 * keeps using, which in the app is the synced server clock.
 */
data class GuessGuard(val consecutiveMisses: Int = 0, val lockedUntilMs: Long? = null) {

  fun isLocked(nowMs: Long): Boolean = lockedUntilMs != null && nowMs < lockedUntilMs

  /** A word the validator refused for [reason] at [nowMs]. */
  fun onRejected(reason: RejectionReason, nowMs: Long): GuessGuard {
    if (reason != RejectionReason.NotAWord) return this
    val misses = consecutiveMisses + 1
    return if (misses >= MaxMisses) GuessGuard(0, nowMs + LockMs) else copy(consecutiveMisses = misses)
  }

  /** A word the validator accepted: the streak of misses is over. */
  fun onAccepted(): GuessGuard = copy(consecutiveMisses = 0)

  companion object {
    const val MaxMisses = 5
    const val LockMs = 5_000L
  }
}
