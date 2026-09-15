// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import br.com.colman.palavramento.domain.board.Rotation
import br.com.colman.palavramento.domain.board.logicalIndexAt

/**
 * Pure pixel/index geometry for [BoardView]: mapping a raw touch position to a logical tile
 * (task brief 6's hit-radius rule) and mapping a logical tile to the display cell it is drawn at
 * under a [Rotation] (task brief 1, for the traced-path overlay). Split out from `BoardView.kt`
 * because none of this needs Compose state or a composable scope - it is plain arithmetic, tested
 * directly in `BoardGeometryTest`.
 */

/**
 * Display-space cell index under [position] when it falls within [RadiusFraction] of a cell's
 * center, or null when [position] is in the dead zone between tiles (task brief 6: "raio menor que
 * o tile, em torno de 40% do seu tamanho a partir do centro").
 */
internal fun displayIndexAt(position: Offset, boxSize: IntSize, gridSize: Int): Int? {
  if (boxSize.width <= 0 || boxSize.height <= 0) return null
  val cellWidth = boxSize.width.toFloat() / gridSize
  val cellHeight = boxSize.height.toFloat() / gridSize
  val col = (position.x / cellWidth).toInt().coerceIn(0, gridSize - 1)
  val row = (position.y / cellHeight).toInt().coerceIn(0, gridSize - 1)
  val centerX = (col + HalfCell) * cellWidth
  val centerY = (row + HalfCell) * cellHeight
  val deltaX = position.x - centerX
  val deltaY = position.y - centerY
  val radius = minOf(cellWidth, cellHeight) * RadiusFraction
  return if (deltaX * deltaX + deltaY * deltaY <= radius * radius) row * gridSize + col else null
}

/**
 * The display-space cell a [logicalIndex] tile is drawn at under [rotation] - the inverse of
 * [logicalIndexAt] (task brief 1: draw the traced path "em display space, seguindo a rotacao").
 * [logicalIndexAt] applies the inverse quarter-turn map (display (row,col) -> logical
 * (size-1-col,row)) [Rotation.quarterTurns] times; applying the forward map ((row,col) ->
 * (col,size-1-row)) the same number of times inverts that back to a display cell.
 */
internal fun displayIndexOf(logicalIndex: Int, size: Int, rotation: Rotation): Int {
  var row = logicalIndex / size
  var col = logicalIndex % size
  repeat(rotation.quarterTurns) {
    val nextRow = col
    val nextCol = size - 1 - row
    row = nextRow
    col = nextCol
  }
  return row * size + col
}

/** Pixel center, in [canvasSize], of the tile drawn at [displayIndex] on a [gridSize]x[gridSize] grid. */
internal fun tileCenter(displayIndex: Int, canvasSize: Size, gridSize: Int): Offset {
  val cellWidth = canvasSize.width / gridSize
  val cellHeight = canvasSize.height / gridSize
  val col = displayIndex % gridSize
  val row = displayIndex / gridSize
  return Offset((col + HalfCell) * cellWidth, (row + HalfCell) * cellHeight)
}

private const val RadiusFraction = 0.4f
private const val HalfCell = 0.5f
