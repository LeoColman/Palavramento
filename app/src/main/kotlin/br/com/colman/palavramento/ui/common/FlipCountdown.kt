// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.colman.palavramento.ui.theme.PalavramentoColors

/** Semantics test tag prefix for a single flip digit, suffixed by its position (0..3). */
const val FlipDigitTestTagPrefix = "flipDigit"

/**
 * `MM:SS` countdown with a flip-style animation per digit (dossier 6.2/6.3, task brief 4), driven
 * entirely by [remainingMs] - callers compute that from [ServerClock][br.com.colman.palavramento.clock.ServerClock]
 * via [rememberRemainingMs], never the wall clock. Turns [PalavramentoColors.rejected] in the last
 * ten seconds. [digitSize] lets the round timer (big) and "Proxima partida em" (smaller variant)
 * share one implementation.
 */
@Composable
fun FlipCountdown(remainingMs: Long, modifier: Modifier = Modifier, digitSize: TextUnit = LargeDigitSize) {
  val colors = PalavramentoColors.current
  val digits = countdownDigits(remainingMs)
  val urgent = isCountdownUrgent(remainingMs)
  val textColor = if (urgent) colors.rejected else colors.textPrimary
  val cardColor = colors.surface

  Row(modifier, horizontalArrangement = Arrangement.spacedBy(DigitGap)) {
    digits.forEachIndexed { index, digit ->
      FlipDigit(digit, textColor, cardColor, digitSize, testTag = "$FlipDigitTestTagPrefix$index")
      if (index == MinutesDigitCount - 1) {
        Text(":", color = textColor, fontSize = digitSize, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun FlipDigit(digit: Int, textColor: Color, cardColor: Color, fontSize: TextUnit, testTag: String) {
  var displayedDigit by remember { mutableIntStateOf(digit) }
  val rotation = remember { Animatable(0f) }
  val halfDuration = animationDurationMillis(FlipHalfDurationMillis)
  val density = LocalDensity.current.density

  LaunchedEffect(digit) {
    if (digit == displayedDigit) return@LaunchedEffect
    if (halfDuration > 0) {
      rotation.animateTo(HalfFlipDegrees, tween(halfDuration))
      displayedDigit = digit
      rotation.animateTo(FullFlipDegrees, tween(halfDuration))
      rotation.snapTo(0f)
    } else {
      displayedDigit = digit
    }
  }

  Box(
    Modifier
      .size(width = digitCardWidth(fontSize), height = digitCardHeight(fontSize))
      .graphicsLayer {
        rotationX = rotation.value
        cameraDistance = CameraDistanceFactor * density
      }
      .background(cardColor, RoundedCornerShape(DigitCornerRadius)),
    contentAlignment = Alignment.Center,
  ) {
    // The tag goes on the Text itself (not the Box): onNodeWithTag()'s default merged-tree lookup
    // only pulls a descendant's text into a tagged ancestor when that ancestor opts into
    // mergeDescendants, which this Box does not need for anything else it does.
    Text(
      text = displayedDigit.toString(),
      color = textColor,
      fontSize = fontSize,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.testTag(testTag),
    )
  }
}

private fun digitCardWidth(fontSize: TextUnit): Dp = (fontSize.value * DigitWidthFactor).dp
private fun digitCardHeight(fontSize: TextUnit): Dp = (fontSize.value * DigitHeightFactor).dp

/** Font size for the round timer (dossier 6.2: "cronometro regressivo grande"). */
val LargeDigitSize = 40.sp

/** Font size for "Proxima partida em MM:SS" (dossier 6.3), a secondary/smaller countdown. */
val SmallDigitSize = 20.sp

private const val MinutesDigitCount = 2
private const val HalfFlipDegrees = 90f
private const val FullFlipDegrees = 180f
private const val FlipHalfDurationMillis = 150
private const val CameraDistanceFactor = 12f
private const val DigitWidthFactor = 0.9f
private const val DigitHeightFactor = 1.3f
private val DigitGap = 2.dp
private val DigitCornerRadius = 6.dp
