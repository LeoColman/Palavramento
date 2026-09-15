// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.common

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.checkAll

class NumberFormatTest : FunSpec({
  context("Averages render with one decimal") {
    withData(
      12.333333333333334 to "12.3",
      18.5 to "18.5",
      // kotlin.math.round rounds half to even, the same rule :domain's RoundStats uses.
      1.25 to "1.2",
      1.26 to "1.3",
      0.0 to "0.0",
      -0.0 to "0.0",
      -0.01 to "0.0",
      7.0 to "7.0",
    ) { (value, text) ->
      value.oneDecimal() shouldBe text
    }
  }

  // Averages are computed from whole scores and word counts, so they are always finite.
  test("Never shows more than one decimal") {
    checkAll(Arb.double(0.0, 10_000.0, includeNaNs = false)) { value ->
      value.oneDecimal() shouldMatch Regex("\\d+\\.\\d")
    }
  }
})
