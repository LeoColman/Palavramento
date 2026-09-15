// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.common

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runAndroidComposeUiTest
import br.com.colman.kotest.FunSpec

/**
 * Compose test for the flip countdown (task brief 4/8): each digit renders correctly on first
 * composition, with no animation to wait out (a digit that matches its own initial state never
 * starts [FlipDigitTestTagPrefix]'s flip, see `FlipCountdown.kt`).
 */
@OptIn(ExperimentalTestApi::class)
class FlipCountdownTest : FunSpec({

  test("73 seconds (01:13) renders as the four digits 0, 1, 1, 3") {
    runAndroidComposeUiTest<ComponentActivity> {
      setContent { FlipCountdown(remainingMs = 73_000) }

      onNodeWithTag("${FlipDigitTestTagPrefix}0").assertTextEquals("0")
      onNodeWithTag("${FlipDigitTestTagPrefix}1").assertTextEquals("1")
      onNodeWithTag("${FlipDigitTestTagPrefix}2").assertTextEquals("1")
      onNodeWithTag("${FlipDigitTestTagPrefix}3").assertTextEquals("3")
    }
  }

  test("Zero renders as 0, 0, 0, 0") {
    runAndroidComposeUiTest<ComponentActivity> {
      setContent { FlipCountdown(remainingMs = 0) }

      onNodeWithTag("${FlipDigitTestTagPrefix}0").assertTextEquals("0")
      onNodeWithTag("${FlipDigitTestTagPrefix}1").assertTextEquals("0")
      onNodeWithTag("${FlipDigitTestTagPrefix}2").assertTextEquals("0")
      onNodeWithTag("${FlipDigitTestTagPrefix}3").assertTextEquals("0")
    }
  }
})
