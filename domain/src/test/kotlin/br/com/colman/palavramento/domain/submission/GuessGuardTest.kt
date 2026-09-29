// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.submission

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

private fun GuessGuard.miss(times: Int, nowMs: Long = 0L): GuessGuard =
  (1..times).fold(this) { guard, _ -> guard.onRejected(RejectionReason.NotAWord, nowMs) }

class GuessGuardTest : FunSpec({

  test("five non-words in a row lock the board for five seconds") {
    GuessGuard.MaxMisses shouldBe 5
    GuessGuard.LockMs shouldBe 5_000L
    val guard = GuessGuard().miss(5, nowMs = 1_000L)

    guard.lockedUntilMs shouldBe 6_000L
    guard.isLocked(1_000L) shouldBe true
    guard.isLocked(5_999L) shouldBe true
    guard.isLocked(6_000L) shouldBe false
  }

  test("four non-words do not lock, and the count is kept") {
    val guard = GuessGuard().miss(4)

    guard.consecutiveMisses shouldBe 4
    guard.lockedUntilMs shouldBe null
    guard.isLocked(0L) shouldBe false
  }

  test("locking starts the count over, so five more are needed for the next lock") {
    val locked = GuessGuard().miss(5, nowMs = 0L)
    locked.consecutiveMisses shouldBe 0

    val again = locked.miss(4, nowMs = 10_000L)
    again.isLocked(10_000L) shouldBe false
    again.miss(1, nowMs = 10_000L).lockedUntilMs shouldBe 15_000L
  }

  test("an accepted word ends the streak") {
    val guard = GuessGuard().miss(4).onAccepted()

    guard.consecutiveMisses shouldBe 0
    guard.miss(4).isLocked(0L) shouldBe false
  }

  test("only a non-word counts: repeats, bad paths and short words leave the guard alone") {
    checkAll(Arb.enum<RejectionReason>(), Arb.long(0L..1_000_000L)) { reason, now ->
      val guard = GuessGuard(consecutiveMisses = 4)
      val next = guard.onRejected(reason, now)
      if (reason == RejectionReason.NotAWord) next.isLocked(now) shouldBe true else next shouldBe guard
    }
  }

  test("a fresh guard is never locked") {
    checkAll(Arb.long()) { now -> GuessGuard().isLocked(now) shouldBe false }
  }

  test("however misses and hits interleave, the lock only follows five misses in a row") {
    checkAll(Arb.list(Arb.enum<Outcome>(), 0..40)) { outcomes ->
      var guard = GuessGuard()
      var streak = 0
      var expectLocked = false
      outcomes.forEach { outcome ->
        when (outcome) {
          Outcome.Miss -> {
            guard = guard.onRejected(RejectionReason.NotAWord, 0L)
            streak++
            if (streak == GuessGuard.MaxMisses) {
              expectLocked = true
              streak = 0
            }
          }
          Outcome.Hit -> {
            guard = guard.onAccepted()
            streak = 0
          }
        }
        guard.consecutiveMisses shouldBe streak
      }
      guard.isLocked(0L) shouldBe expectLocked
    }
  }
})

private enum class Outcome { Miss, Hit }
