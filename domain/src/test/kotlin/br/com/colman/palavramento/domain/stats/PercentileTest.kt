// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.stats

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PercentileTest : FunSpec({
  test("A single player is 0 percent, dossier's explicit edge case") {
    Percentile.of(rank = 1, totalPlayers = 1) shouldBe 0
  }

  test("The top rank of many is close to 100") {
    Percentile.of(rank = 1, totalPlayers = 10) shouldBe 100 // (10-1)/(10-1)*100
  }

  test("The last rank is 0") {
    Percentile.of(rank = 10, totalPlayers = 10) shouldBe 0
  }

  test("A middle rank matches the dossier formula") {
    Percentile.of(rank = 3, totalPlayers = 10) shouldBe 77 // floor((10-3)/9*100) = floor(77.77) = 77
  }

  test("Rejects a rank outside 1..totalPlayers, or fewer than 1 player") {
    shouldThrow<IllegalArgumentException> { Percentile.of(rank = 0, totalPlayers = 5) }
    shouldThrow<IllegalArgumentException> { Percentile.of(rank = 6, totalPlayers = 5) }
    shouldThrow<IllegalArgumentException> { Percentile.of(rank = 1, totalPlayers = 0) }
  }
})
