// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.round

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Pure timing-math tests with a fixed [Instant] (dossier phase 3 task: "unit tests for scheduler
 * timing math with a fake clock"). No coroutine, no database, no real waiting.
 */
class RoundTimingTest : FunSpec({
  val now = Instant.parse("2026-01-01T00:00:00Z")

  test("firstRoundStartsAt is now plus the intermission") {
    RoundTiming.firstRoundStartsAt(now, 60.seconds) shouldBe now.plusSeconds(60)
  }

  test("endsAt is startsAt plus the round duration") {
    RoundTiming.endsAt(now, 120.seconds) shouldBe now.plusSeconds(120)
  }

  test("nextRoundStartsAt is the previous round's end plus the intermission") {
    RoundTiming.nextRoundStartsAt(now, 60.seconds) shouldBe now.plusSeconds(60)
  }

  test("a full round window chains starts, ends and the next round's start correctly") {
    val startsAt = RoundTiming.firstRoundStartsAt(now, 60.seconds)
    val endsAt = RoundTiming.endsAt(startsAt, 120.seconds)
    val nextStartsAt = RoundTiming.nextRoundStartsAt(endsAt, 60.seconds)

    startsAt shouldBe now.plusSeconds(60)
    endsAt shouldBe now.plusSeconds(180)
    nextStartsAt shouldBe now.plusSeconds(240)
  }

  test("lateSubmissionDeadline is endsAt plus the tolerance") {
    RoundTiming.lateSubmissionDeadline(now, 500.milliseconds) shouldBe now.plusMillis(500)
  }

  test("canLateJoin allows joining with more than the minimum remaining") {
    val endsAt = now.plusSeconds(60)
    RoundTiming.canLateJoin(now, endsAt, 10.seconds) shouldBe true
  }

  test("canLateJoin allows joining with exactly the minimum remaining (the boundary is allowed)") {
    val endsAt = now.plusSeconds(10)
    RoundTiming.canLateJoin(now, endsAt, 10.seconds) shouldBe true
  }

  test("canLateJoin refuses joining with one millisecond less than the minimum remaining") {
    val endsAt = now.plusSeconds(10).minusMillis(1)
    RoundTiming.canLateJoin(now, endsAt, 10.seconds) shouldBe false
  }

  test("canLateJoin refuses joining once the round has already ended") {
    val endsAt = now.minusSeconds(1)
    RoundTiming.canLateJoin(now, endsAt, 10.seconds) shouldBe false
  }

  test("MutableGameClock only moves when advanced or set") {
    val clock = MutableGameClock(now)
    clock.now() shouldBe now

    clock.advance(1500)
    clock.now() shouldBe now.plusMillis(1500)

    val later = Instant.parse("2027-01-01T00:00:00Z")
    clock.set(later)
    clock.now() shouldBe later
  }
})
