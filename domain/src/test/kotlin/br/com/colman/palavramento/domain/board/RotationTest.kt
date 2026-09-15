// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.board

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

class RotationTest : FunSpec({
  test("Each rotation exposes its own degrees and quarter turn count") {
    Rotation.Deg0.degrees shouldBe 0
    Rotation.Deg90.degrees shouldBe 90
    Rotation.Deg180.degrees shouldBe 180
    Rotation.Deg270.degrees shouldBe 270
    Rotation.Deg0.quarterTurns shouldBe 0
    Rotation.Deg90.quarterTurns shouldBe 1
    Rotation.Deg180.quarterTurns shouldBe 2
    Rotation.Deg270.quarterTurns shouldBe 3
  }

  test("Deg0 is the identity") {
    for (index in 0 until 16) {
      logicalIndexAt(index, size = 4, Rotation.Deg0) shouldBe index
    }
  }

  test("A known 90-degree rotation maps corners as expected") {
    // On a 4x4 board rotated 90 degrees clockwise, the tile now shown at the top-left corner
    // (display index 0) is the logical bottom-left corner (row 3, col 0 = index 12).
    logicalIndexAt(displayIndex = 0, size = 4, Rotation.Deg90) shouldBe 12
    logicalIndexAt(displayIndex = 3, size = 4, Rotation.Deg90) shouldBe 0
    logicalIndexAt(displayIndex = 15, size = 4, Rotation.Deg90) shouldBe 3
    logicalIndexAt(displayIndex = 12, size = 4, Rotation.Deg90) shouldBe 15
  }

  test("Rotating four times (360 degrees) is the identity, for any size and index") {
    checkAll(Arb.int(1..6), Arb.int(0..35)) { size, rawIndex ->
      val index = rawIndex % (size * size)
      var result = index
      repeat(4) { result = logicalIndexAt(result, size, Rotation.Deg90) }
      result shouldBe index
    }
  }

  test("Deg180 equals applying Deg90 twice") {
    checkAll(Arb.int(1..6), Arb.int(0..35)) { size, rawIndex ->
      val index = rawIndex % (size * size)
      val twice = logicalIndexAt(logicalIndexAt(index, size, Rotation.Deg90), size, Rotation.Deg90)
      logicalIndexAt(index, size, Rotation.Deg180) shouldBe twice
    }
  }

  test("Every rotation produces an index within bounds") {
    checkAll(Arb.int(1..6), Arb.int(0..35)) { size, rawIndex ->
      val index = rawIndex % (size * size)
      Rotation.entries.forEach { rotation ->
        logicalIndexAt(index, size, rotation) shouldBeInRange 0..(size * size - 1)
      }
    }
  }
})
