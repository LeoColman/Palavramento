// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.board

/**
 * The "Girar" button rotates the grid on screen only: it never changes logical indices or scoring
 * (dossier 1.1). This is the pure mapping the app needs to know which logical tile is drawn at a
 * given display cell after rotating the board clockwise by a multiple of 90 degrees.
 */
enum class Rotation(val degrees: Int) {
  Deg0(Degrees0),
  Deg90(Degrees90),
  Deg180(Degrees180),
  Deg270(Degrees270),
  ;

  /** Number of 90-degree clockwise turns this rotation represents. */
  val quarterTurns: Int get() = degrees / Degrees90
}

// File-level, not a Rotation companion: an enum's constants build before its own companion exists,
// so a constant used in a constant's constructor call has to live outside the enum entirely.
private const val Degrees0 = 0
private const val Degrees90 = 90
private const val Degrees180 = 180
private const val Degrees270 = 270

/**
 * Logical board index of the tile drawn at [displayIndex] once the grid has been rotated
 * [rotation] clockwise.
 *
 * One 90-degree clockwise turn maps logical (row, col) to display (col, size - 1 - row); inverting
 * that gives the logical cell for a display cell as (size - 1 - col, row). The function applies that
 * inverse [Rotation.quarterTurns] times, so a full 360-degree turn (4 quarter turns) is the identity.
 */
fun logicalIndexAt(displayIndex: Int, size: Int, rotation: Rotation): Int {
  var row = displayIndex / size
  var col = displayIndex % size
  repeat(rotation.quarterTurns) {
    val nextRow = size - 1 - col
    val nextCol = row
    row = nextRow
    col = nextCol
  }
  return row * size + col
}
