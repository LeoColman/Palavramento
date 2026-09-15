// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import br.com.colman.palavramento.domain.board.Rotation
import br.com.colman.palavramento.domain.board.logicalIndexAt
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

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
})
