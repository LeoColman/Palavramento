// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.common

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

class TimeFormatTest : FunSpec({

  test("Zero formats as 00:00") {
    formatCountdown(0) shouldBe "00:00"
  }

  test("120 seconds formats as 02:00 (dossier 1.4 round duration)") {
    formatCountdown(120_000) shouldBe "02:00"
  }

  test("73 seconds formats as 01:13") {
    formatCountdown(73_000) shouldBe "01:13"
  }

  test("A negative value clamps to 00:00 instead of printing negative time") {
    formatCountdown(-5_000) shouldBe "00:00"
  }

  test("The format is always MM:SS, two digits each, for any input under 100 minutes") {
    // Every countdown in the app (120s rounds, 60s between them) is well under 100 minutes; that
    // is the range in which minutes stay exactly two digits.
    checkAll(Arb.long(0L..5_999_000L)) { ms ->
      formatCountdown(ms).matches(Regex("""\d{2}:\d{2}""")) shouldBe true
    }
  }

  test("countdownDigits splits 01:13 into its four digits, in display order") {
    countdownDigits(73_000) shouldBe listOf(0, 1, 1, 3)
  }

  test("countdownDigits always has exactly four digits, matching formatCountdown") {
    checkAll(Arb.long(0L..5_999_000L)) { ms ->
      countdownDigits(ms).joinToString("") shouldBe formatCountdown(ms).filter { it != ':' }
    }
  }

  test("isCountdownUrgent is true only in the last ten seconds, not at exactly zero") {
    isCountdownUrgent(10_000) shouldBe true
    isCountdownUrgent(1) shouldBe true
    isCountdownUrgent(0) shouldBe false
    isCountdownUrgent(10_001) shouldBe false
    isCountdownUrgent(-1) shouldBe false
  }
})
