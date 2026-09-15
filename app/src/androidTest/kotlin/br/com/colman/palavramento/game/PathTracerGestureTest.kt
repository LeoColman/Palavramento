// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.game

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runAndroidComposeUiTest
import androidx.compose.ui.test.up
import br.com.colman.kotest.FunSpec
import br.com.colman.palavramento.domain.board.Rotation
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.ui.room.BoardTestTag
import br.com.colman.palavramento.ui.room.BoardView
import br.com.colman.palavramento.ui.room.TracedWordTestTag

// 4x4, row-major: A B C D / E F G H / I J K L / M N O P. Tile value = index + 1, so the traced
// word's score in the assertions below is just the sum of the letters' 1-based positions.
private fun sampleTiles(): List<Tile> = List(16) { index -> Tile(('A' + index).toString(), index + 1) }

/**
 * Compose UI tests for the drag-to-trace gesture (dossier 6.2, dossier 10: "testes de Compose para
 * o gesto de tracado: anexar, desfazer ao retroceder, rejeitar nao adjacente"). [BoardView] is
 * exercised directly, with no ViewModel/Koin involved: the gesture rules live entirely in
 * [PathTracer], and this only needs to prove the composable wires pointer input to it correctly.
 */
@OptIn(ExperimentalTestApi::class)
class PathTracerGestureTest : FunSpec({

  test("Dragging onto an adjacent tile appends it to the traced word") {
    runAndroidComposeUiTest<ComponentActivity> {
      setContent {
        BoardView(tiles = sampleTiles(), rotation = Rotation.Deg0, onSubmit = {})
      }

      onNodeWithTag(BoardTestTag).performTouchInput {
        val cellWidth = width / GridSize
        val cellHeight = height / GridSize
        startDragGradually(cellCenter(0, 0, cellWidth, cellHeight), cellCenter(0, 1, cellWidth, cellHeight))
      }

      // A (value 1) + B (value 2) = 3.
      onNodeWithTag(TracedWordTestTag).assertTextEquals("AB (3)")

      onNodeWithTag(BoardTestTag).performTouchInput { up() }
    }
  }

  test("Dragging back onto the second-to-last tile undoes the last append") {
    runAndroidComposeUiTest<ComponentActivity> {
      setContent {
        BoardView(tiles = sampleTiles(), rotation = Rotation.Deg0, onSubmit = {})
      }

      onNodeWithTag(BoardTestTag).performTouchInput {
        val cellWidth = width / GridSize
        val cellHeight = height / GridSize
        // A(0,0) -> B(0,1) -> F(1,1): F is adjacent to B (8-direction adjacency).
        startDragGradually(cellCenter(0, 0, cellWidth, cellHeight), cellCenter(0, 1, cellWidth, cellHeight))
        moveTo(cellCenter(1, 1, cellWidth, cellHeight))
      }
      onNodeWithTag(TracedWordTestTag).assertTextEquals("ABF (9)")

      onNodeWithTag(BoardTestTag).performTouchInput {
        val cellWidth = width / GridSize
        val cellHeight = height / GridSize
        // B is the second-to-last tile of A-B-F: entering it again is the undo gesture.
        moveTo(cellCenter(0, 1, cellWidth, cellHeight))
      }
      onNodeWithTag(TracedWordTestTag).assertTextEquals("AB (3)")

      onNodeWithTag(BoardTestTag).performTouchInput { up() }
    }
  }

  test("Dragging onto a non-adjacent tile is ignored") {
    runAndroidComposeUiTest<ComponentActivity> {
      setContent {
        BoardView(tiles = sampleTiles(), rotation = Rotation.Deg0, onSubmit = {})
      }

      onNodeWithTag(BoardTestTag).performTouchInput {
        val cellWidth = width / GridSize
        val cellHeight = height / GridSize
        startDragGradually(cellCenter(0, 0, cellWidth, cellHeight), cellCenter(0, 1, cellWidth, cellHeight))
      }
      onNodeWithTag(TracedWordTestTag).assertTextEquals("AB (3)")

      onNodeWithTag(BoardTestTag).performTouchInput {
        val cellWidth = width / GridSize
        val cellHeight = height / GridSize
        // P(3,3) is far from the last tile, B(0,1): not adjacent, so this must be a no-op. A drag
        // already in progress delivers this as one plain move (no touch-slop gate to cross again).
        moveTo(cellCenter(3, 3, cellWidth, cellHeight))
      }
      onNodeWithTag(TracedWordTestTag).assertTextEquals("AB (3)")

      onNodeWithTag(BoardTestTag).performTouchInput { up() }
    }
  }
})

private const val GridSize = 4
private const val HalfCell = 0.5f
private const val StartDragSteps = 12

private fun cellCenter(row: Int, col: Int, cellWidth: Int, cellHeight: Int): Offset =
  Offset((col + HalfCell) * cellWidth, (row + HalfCell) * cellHeight)

/**
 * Presses down at [from] and moves to [firstTarget] in small steps, rather than one big jump.
 * `detectDragGestures` only recognizes a drag once the pointer clears touch slop, and reports
 * `onDragStart` at wherever that clearing happened; with a single huge jump straight from `from`
 * to `firstTarget`, that point can already read as `firstTarget`, silently dropping `from` before
 * [PathTracer] ever sees it. Moving in small steps keeps the early ones (where slop is actually
 * consumed) well inside `from`'s own tile, so the drag genuinely starts there - matching a real
 * finger, which never teleports either.
 */
private fun TouchInjectionScope.startDragGradually(from: Offset, firstTarget: Offset) {
  down(from)
  for (step in 1..StartDragSteps) {
    val fraction = step / StartDragSteps.toFloat()
    moveTo(
      Offset(
        from.x + (firstTarget.x - from.x) * fraction,
        from.y + (firstTarget.y - from.y) * fraction,
      ),
    )
  }
}
