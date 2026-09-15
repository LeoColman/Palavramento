// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.audio

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

class MusicSpeedCurveTest : FunSpec({

  test("More than 30s left plays at base speed") {
    MusicSpeedCurve.speedFor(30_000) shouldBe MusicSpeedCurve.BaseSpeed
    MusicSpeedCurve.speedFor(45_000) shouldBe MusicSpeedCurve.BaseSpeed
    MusicSpeedCurve.speedFor(120_000) shouldBe MusicSpeedCurve.BaseSpeed
  }

  test("10s or less left plays at the peak speed, a step up from the ramp") {
    MusicSpeedCurve.speedFor(10_000) shouldBe MusicSpeedCurve.PeakSpeed
    MusicSpeedCurve.speedFor(5_000) shouldBe MusicSpeedCurve.PeakSpeed
    MusicSpeedCurve.speedFor(0) shouldBe MusicSpeedCurve.PeakSpeed
  }

  test("The midpoint of the ramp window sits halfway between base and ramp speed") {
    val midpoint = (MusicSpeedCurve.RampStartMs + MusicSpeedCurve.UrgentThresholdMs) / 2
    val expected = (MusicSpeedCurve.BaseSpeed + MusicSpeedCurve.RampSpeed) / 2
    MusicSpeedCurve.speedFor(midpoint) shouldBe expected
  }

  test("A negative remaining time is treated exactly like zero") {
    MusicSpeedCurve.speedFor(-1_000) shouldBe MusicSpeedCurve.speedFor(0)
  }

  test("The speed is always within [BaseSpeed, PeakSpeed]") {
    checkAll(Arb.long(-10_000L..600_000L)) { remainingMs ->
      val speed = MusicSpeedCurve.speedFor(remainingMs)
      (speed >= MusicSpeedCurve.BaseSpeed && speed <= MusicSpeedCurve.PeakSpeed) shouldBe true
    }
  }

  test("The speed never decreases as less time is left (monotonic)") {
    checkAll(Arb.long(-10_000L..600_000L), Arb.long(-10_000L..600_000L)) { a, b ->
      val (lessRemaining, moreRemaining) = if (a <= b) a to b else b to a
      (MusicSpeedCurve.speedFor(lessRemaining) >= MusicSpeedCurve.speedFor(moreRemaining)) shouldBe true
    }
  }
})
