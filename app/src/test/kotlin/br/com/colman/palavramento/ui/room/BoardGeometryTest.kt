// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import br.com.colman.palavramento.domain.board.Rotation
import br.com.colman.palavramento.domain.board.logicalIndexAt
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

// A 400x400 box on a 4x4 grid: 100 px cells, centers on the 50/150/250/350 lines, hit radius
// 0.4 * 100 = 40 px. Every number below is exact in binary floating point on purpose.
private val Box = IntSize(400, 400)
private val Canvas = Size(400f, 400f)

/**
 * [displayIndexOf] is the inverse of [logicalIndexAt] (task brief 1: draw the traced path in
 * display space, following rotation). Exhaustively checked rather than with a property arb: both
 * the grid sizes in play (dossier: only 4x4 ships, but the domain's own tests exercise 3x3 too) and
 * [Rotation] are small, closed sets.
 */
class BoardGeometryTest : FunSpec({

  test("displayIndexOf inverts logicalIndexAt for every cell, size and rotation") {
    for (size in 1..5) {
      for (rotation in Rotation.entries) {
        for (logicalIndex in 0 until size * size) {
          val display = displayIndexOf(logicalIndex, size, rotation)
          logicalIndexAt(display, size, rotation) shouldBe logicalIndex
        }
      }
    }
  }

  test("logicalIndexAt inverts displayIndexOf for every cell, size and rotation") {
    for (size in 1..5) {
      for (rotation in Rotation.entries) {
        for (displayIndex in 0 until size * size) {
          val logical = logicalIndexAt(displayIndex, size, rotation)
          displayIndexOf(logical, size, rotation) shouldBe displayIndex
        }
      }
    }
  }

  test("Deg0 is the identity in both directions") {
    for (index in 0 until 16) {
      displayIndexOf(index, 4, Rotation.Deg0) shouldBe index
    }
  }

  test("displayIndexAt hits the cell whose center the touch is on, row-major") {
    displayIndexAt(Offset(50f, 50f), Box, 4) shouldBe 0
    displayIndexAt(Offset(350f, 50f), Box, 4) shouldBe 3
    displayIndexAt(Offset(50f, 350f), Box, 4) shouldBe 12
    displayIndexAt(Offset(150f, 150f), Box, 4) shouldBe 5
    displayIndexAt(Offset(350f, 350f), Box, 4) shouldBe 15
  }

  test("The hit radius is 40% of the cell, and the boundary itself counts as a hit") {
    // Exactly on the radius, straight along each axis.
    displayIndexAt(Offset(190f, 150f), Box, 4) shouldBe 5
    displayIndexAt(Offset(110f, 150f), Box, 4) shouldBe 5
    displayIndexAt(Offset(150f, 190f), Box, 4) shouldBe 5
    displayIndexAt(Offset(150f, 110f), Box, 4) shouldBe 5

    // One pixel past it, in the dead zone the task brief asks for.
    displayIndexAt(Offset(191f, 150f), Box, 4).shouldBeNull()
    displayIndexAt(Offset(150f, 191f), Box, 4).shouldBeNull()
  }

  test("The corner between four tiles is dead zone, not the nearest tile") {
    displayIndexAt(Offset(100f, 100f), Box, 4).shouldBeNull()
    displayIndexAt(Offset(200f, 200f), Box, 4).shouldBeNull()
  }

  test("A touch past the edge clamps to the last row or column, and lands in its dead zone") {
    // Clamping matters: without it the computed center would follow the finger and every
    // out-of-bounds touch would report a tile that is not on the board at all.
    displayIndexAt(Offset(450f, 150f), Box, 4).shouldBeNull()
    displayIndexAt(Offset(150f, 450f), Box, 4).shouldBeNull()
    displayIndexAt(Offset(-50f, 150f), Box, 4).shouldBeNull()
    displayIndexAt(Offset(-50f, -50f), Box, 4).shouldBeNull()
  }

  test("A box with no width or no height has no tiles to hit") {
    displayIndexAt(Offset(50f, 50f), IntSize(0, 400), 4).shouldBeNull()
    displayIndexAt(Offset(50f, 50f), IntSize(400, 0), 4).shouldBeNull()
    displayIndexAt(Offset(50f, 50f), IntSize(0, 0), 4).shouldBeNull()
  }

  test("On a non-square box the radius follows the shorter cell side") {
    // 400x200 on a 4x4 grid: 100x50 cells, so the radius is 0.4 * 50 = 20, not 40.
    val wide = IntSize(400, 200)
    displayIndexAt(Offset(170f, 75f), wide, 4) shouldBe 5
    displayIndexAt(Offset(171f, 75f), wide, 4).shouldBeNull()
  }

  test("tileCenter is the pixel middle of the cell drawn at that display index") {
    tileCenter(0, Canvas, 4) shouldBe Offset(50f, 50f)
    tileCenter(3, Canvas, 4) shouldBe Offset(350f, 50f)
    tileCenter(4, Canvas, 4) shouldBe Offset(50f, 150f)
    tileCenter(5, Canvas, 4) shouldBe Offset(150f, 150f)
    tileCenter(15, Canvas, 4) shouldBe Offset(350f, 350f)
  }

  test("tileCenter follows a non-square canvas on each axis independently") {
    // 400x200 on a 4x4 grid: 100 wide, 50 tall.
    val wide = Size(400f, 200f)
    tileCenter(0, wide, 4) shouldBe Offset(50f, 25f)
    tileCenter(6, wide, 4) shouldBe Offset(250f, 75f)
  }

  test("tileCenter inverts displayIndexAt: the center it reports is always a hit on that tile") {
    for (index in 0 until 16) {
      displayIndexAt(tileCenter(index, Canvas, 4), Box, 4) shouldBe index
    }
  }
})
