// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runAndroidComposeUiTest
import br.com.colman.kotest.FunSpec
import br.com.colman.palavramento.domain.board.Rotation
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.state.SubmissionFeedback
import br.com.colman.palavramento.ui.common.LocalAnimationsEnabled
import br.com.colman.palavramento.ui.theme.DarkPalavramentoColors
import br.com.colman.palavramento.ui.theme.LocalPalavramentoColors
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

private fun plainTiles(): List<Tile> = List(16) { index -> Tile(('A' + index).toString(), index + 1) }

/** How many pixels of the whole board are [color] right now. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.pixelsOf(color: Color): Int {
  val pixels = onRoot().captureToImage().toPixelMap()
  var count = 0
  for (x in 0 until pixels.width) {
    for (y in 0 until pixels.height) {
      if (pixels[x, y] == color) count++
    }
  }
  return count
}

/**
 * A player with the system "remove animations" setting on (Accessibility, or a battery saver that
 * forces it) used to get no accept flash at all: the board set the green and cleared it inside the
 * same frame, so found words looked identical to nothing happening. The flash is the verdict on the
 * word, so it now lasts its full time whatever that setting says; only the shake and the color
 * tween follow it.
 */
@OptIn(ExperimentalTestApi::class)
class BoardFeedbackFlashTest : FunSpec({

  test("an accepted word turns its tiles green even with system animations off") {
    runAndroidComposeUiTest<ComponentActivity> {
      mainClock.autoAdvance = false
      setContent {
        CompositionLocalProvider(
          LocalAnimationsEnabled provides false,
          LocalPalavramentoColors provides DarkPalavramentoColors,
        ) {
          BoardView(
            tiles = plainTiles(),
            rotation = Rotation.Deg0,
            feedback = SubmissionFeedback.Accepted("abc", 3, listOf(0, 1)),
            onSubmit = {},
          )
        }
      }

      mainClock.advanceTimeBy(FlashSampleMillis)

      pixelsOf(DarkPalavramentoColors.accepted) shouldBeGreaterThan 0
    }
  }

  test("the flash clears itself, so the board does not stay green") {
    runAndroidComposeUiTest<ComponentActivity> {
      mainClock.autoAdvance = false
      setContent {
        CompositionLocalProvider(
          LocalAnimationsEnabled provides false,
          LocalPalavramentoColors provides DarkPalavramentoColors,
        ) {
          BoardView(
            tiles = plainTiles(),
            rotation = Rotation.Deg0,
            feedback = SubmissionFeedback.Accepted("abc", 3, listOf(0, 1)),
            onSubmit = {},
          )
        }
      }

      mainClock.advanceTimeBy(AfterFlashMillis)

      pixelsOf(DarkPalavramentoColors.accepted) shouldBe 0
    }
  }
})

/** Well inside the flash, and well past it: the flash itself lasts 500 ms (`BoardView`). */
private const val FlashSampleMillis = 100L
private const val AfterFlashMillis = 2_000L
