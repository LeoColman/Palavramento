// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Rotation
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.board.logicalIndexAt
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.mutator.effectiveValueOf
import br.com.colman.palavramento.game.PathTracer
import br.com.colman.palavramento.ui.theme.PalavramentoColors
import kotlin.math.sqrt

/** Semantics test tag for the board's drag surface, used by the Compose gesture tests. */
const val BoardTestTag = "board"

/** Semantics test tag for the in-progress traced word, used by the Compose gesture tests. */
const val TracedWordTestTag = "tracedWord"

/**
 * The 4x4 board (dossier 6.2): square orange tiles, value top-left, letter centered, a
 * [Mutator.ForbiddenLetter] tile shown grey (dossier 1.5), and the continuous drag-to-trace gesture
 * (dossier 6.2, task brief 6): entering a tile's hit radius (40% of the tile size from its center)
 * appends it when adjacent to the last tile and unused; re-entering the second-to-last tile undoes
 * the last append; anything else is ignored; releasing submits the path (single-tile paths ignored).
 *
 * [rotation] only changes which logical tile is drawn at which display cell
 * ([br.com.colman.palavramento.domain.board.logicalIndexAt]); [onSubmit] always receives logical
 * indices, never display ones.
 */
@Composable
fun BoardView(
  tiles: List<Tile>,
  mutator: Mutator,
  rotation: Rotation,
  onSubmit: (List<Int>) -> Unit,
  modifier: Modifier = Modifier,
) {
  val gridSize = remember(tiles) { sqrt(tiles.size.toDouble()).toInt() }
  val board = remember(tiles) { Board(gridSize, tiles) }
  val tracer = remember(tiles) { PathTracer(board) }
  var path by remember(tiles) { mutableStateOf(emptyList<Int>()) }
  var boxSize by remember { mutableStateOf(IntSize.Zero) }
  val haptics = LocalHapticFeedback.current

  fun handlePointer(offset: Offset) {
    val displayIndex = displayIndexAt(offset, boxSize, gridSize) ?: return
    val logicalIndex = logicalIndexAt(displayIndex, gridSize, rotation)
    if (tracer.onTileEntered(logicalIndex)) path = tracer.path
  }

  fun endDrag() {
    if (tracer.path.size >= MinSubmittablePathLength) {
      onSubmit(tracer.path)
      haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    tracer.clear()
    path = emptyList()
  }

  Column(modifier) {
    Box(
      Modifier
        .testTag(BoardTestTag)
        .fillMaxWidth()
        .aspectRatio(1f)
        .onSizeChanged { boxSize = it }
        .pointerInput(tiles, rotation) {
          detectDragGestures(
            onDragStart = { offset -> handlePointer(offset) },
            onDrag = { change, _ -> handlePointer(change.position) },
            onDragEnd = { endDrag() },
            onDragCancel = { endDrag() },
          )
        },
    ) {
      Column(Modifier.fillMaxSize()) {
        repeat(gridSize) { row ->
          Row(Modifier.weight(1f).fillMaxWidth()) {
            repeat(gridSize) { col ->
              val displayIndex = row * gridSize + col
              val logicalIndex = logicalIndexAt(displayIndex, gridSize, rotation)
              val tile = tiles[logicalIndex]
              BoardTile(
                tile = tile,
                value = mutator.effectiveValueOf(tile),
                isForbidden = mutator.isForbiddenTile(tile),
                isTraced = logicalIndex in path,
                modifier = Modifier.weight(1f).fillMaxWidth().aspectRatio(1f),
              )
            }
          }
        }
      }
    }

    val word = path.joinToString(separator = "") { tiles[it].letters }
    val score = path.sumOf { mutator.effectiveValueOf(tiles[it]) }
    Text(
      text = if (word.isEmpty()) "" else "$word ($score)",
      modifier = Modifier.padding(top = 8.dp).testTag(TracedWordTestTag),
      color = PalavramentoColors.current.textPrimary,
    )
  }
}

@Composable
private fun BoardTile(tile: Tile, value: Int, isForbidden: Boolean, isTraced: Boolean, modifier: Modifier = Modifier) {
  val colors = PalavramentoColors.current
  val background = when {
    isForbidden -> colors.tileForbidden
    isTraced -> colors.highlight
    else -> colors.tileBackground
  }
  Box(
    modifier
      .padding(TileGap)
      .background(background, RoundedCornerShape(TileCornerRadius)),
  ) {
    Text(
      text = value.toString(),
      color = colors.tileValueText,
      modifier = Modifier.align(Alignment.TopStart).padding(TileValuePadding),
    )
    Text(
      text = tile.letters,
      color = colors.tileText,
      modifier = Modifier.align(Alignment.Center),
    )
  }
}

/**
 * Display-space cell index under [position] when it falls within [RadiusFraction] of a cell's
 * center, or null when [position] is in the dead zone between tiles (task brief 6: "raio menor que
 * o tile, em torno de 40% do seu tamanho a partir do centro").
 */
private fun displayIndexAt(position: Offset, boxSize: IntSize, gridSize: Int): Int? {
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

private fun Mutator.isForbiddenTile(tile: Tile): Boolean = this is Mutator.ForbiddenLetter && letter in tile.letters

private const val MinSubmittablePathLength = 2
private const val RadiusFraction = 0.4f
private const val HalfCell = 0.5f
private val TileGap = 3.dp
private val TileCornerRadius = 8.dp
private val TileValuePadding = PaddingValues(4.dp)
