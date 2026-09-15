// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.clock

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

class ServerClockTest : FunSpec({

  test("nowMs adds the offset to the injected elapsed-realtime reading") {
    checkAll(Arb.long(0L..1_000_000_000L), Arb.long(-1_000_000L..1_000_000L)) { elapsed, offset ->
      val clock = ServerClock(offset) { elapsed }
      clock.nowMs() shouldBe elapsed + offset
    }
  }

  test("A zero offset makes nowMs track the elapsed-realtime provider exactly") {
    var elapsed = 0L
    val clock = ServerClock(0) { elapsed }
    elapsed = 5_000
    clock.nowMs() shouldBe 5_000
    elapsed = 5_100
    clock.nowMs() shouldBe 5_100
  }

  test("nowMs never reads the wall clock: only the injected provider moves it") {
    val provider = { 42L }
    val clock = ServerClock(offsetMs = 8, elapsedRealtimeMs = provider)
    clock.nowMs() shouldBe 50
    clock.nowMs() shouldBe 50
  }
})
