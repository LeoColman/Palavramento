// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runAndroidComposeUiTest
import br.com.colman.kotest.FunSpec
import br.com.colman.palavramento.domain.board.Rotation
import br.com.colman.palavramento.domain.board.Tile

/** One digraph tile plus 15 plain single-letter tiles, a 4x4 board (dossier 1.1). */
private fun tilesWithOneDigraph(): List<Tile> =
  listOf(Tile("QU", 11)) + List(15) { index -> Tile(('A' + index).toString(), index + 1) }

/** One alternatives tile plus 15 plain single-letter tiles, a 4x4 board (ADR 0015). */
private fun tilesWithOneAlternatives(): List<Tile> =
  listOf(Tile("A/F", 20)) + List(15) { index -> Tile(('A' + index).toString(), index + 1) }

/**
 * ADR 0012 acceptance: a two-letter digraph tile (e.g. `QU`, from the `DIGRAFOS` mutator) must render
 * legibly, its letters fitting inside the tile. [BoardView] gives a multi-letter tile a smaller font
 * than a single-letter one (`BoardTile` in `BoardView.kt`); this proves the full two-letter text is
 * actually drawn on screen, not clipped or ellipsized, alongside an ordinary single-letter tile.
 *
 * ADR 0015 acceptance: an alternatives tile (e.g. `A/F`, from the `UMA_OU_OUTRA` mutator) must render
 * its full three-character text ("A/F") the same way, at an even smaller font (`BoardTile` gives it
 * the smallest of the three fractions).
 */
@OptIn(ExperimentalTestApi::class)
class BoardTileRenderTest : FunSpec({

  test("A digraph tile renders its full two-letter text") {
    runAndroidComposeUiTest<ComponentActivity> {
      setContent {
        BoardView(
          tiles = tilesWithOneDigraph(),
          rotation = Rotation.Deg0,
          onSubmit = {},
        )
      }
      onNodeWithText("QU").assertTextEquals("QU")
    }
  }

  test("A single-letter tile still renders normally alongside a digraph tile") {
    runAndroidComposeUiTest<ComponentActivity> {
      setContent {
        BoardView(
          tiles = tilesWithOneDigraph(),
          rotation = Rotation.Deg0,
          onSubmit = {},
        )
      }
      onNodeWithText("A").assertTextEquals("A")
    }
  }

  test("An alternatives tile renders its full 'A/F' text") {
    runAndroidComposeUiTest<ComponentActivity> {
      setContent {
        BoardView(
          tiles = tilesWithOneAlternatives(),
          rotation = Rotation.Deg0,
          onSubmit = {},
        )
      }
      onNodeWithText("A/F").assertTextEquals("A/F")
    }
  }

  test("A single-letter tile still renders normally alongside an alternatives tile") {
    runAndroidComposeUiTest<ComponentActivity> {
      setContent {
        BoardView(
          tiles = tilesWithOneAlternatives(),
          rotation = Rotation.Deg0,
          onSubmit = {},
        )
      }
      onNodeWithText("B").assertTextEquals("B")
    }
  }
})
