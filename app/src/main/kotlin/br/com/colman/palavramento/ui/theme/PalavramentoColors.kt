// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Named color palette for the whole app (dossier 6.5: dark palette only, blue for the match
 * screen, wine for results, orange tiles; no light theme in v1). Screens read colors from
 * [PalavramentoColors] instead of hardcoding [Color] values, so the palette stays in one place.
 */
data class PalavramentoColorPalette(
  val background: Color,
  val surface: Color,
  val surfaceVariant: Color,
  val matchPrimary: Color,
  val matchAccent: Color,
  val resultsPrimary: Color,
  val resultsAccent: Color,
  val tileBackground: Color,
  val tileForbidden: Color,
  val tileText: Color,
  val tileValueText: Color,
  val textPrimary: Color,
  val textSecondary: Color,
  val textDisabled: Color,
  val accepted: Color,
  val rejected: Color,
  val highlight: Color,
)

val DarkPalavramentoColors = PalavramentoColorPalette(
  background = Color(0xFF0B1B34),
  surface = Color(0xFF122A4D),
  surfaceVariant = Color(0xFF1B3760),
  matchPrimary = Color(0xFF15315C),
  matchAccent = Color(0xFF3E7BD6),
  resultsPrimary = Color(0xFF4A1526),
  resultsAccent = Color(0xFF8C2B4C),
  tileBackground = Color(0xFFE08A2B),
  tileForbidden = Color(0xFF5A5A5A),
  tileText = Color(0xFF1A1005),
  tileValueText = Color(0xFF4A2E05),
  textPrimary = Color(0xFFF5F0E6),
  textSecondary = Color(0xFFB9C2D0),
  textDisabled = Color(0xFF6B7686),
  accepted = Color(0xFF4CAF7D),
  rejected = Color(0xFFD9534F),
  highlight = Color(0xFFE0B84A),
)

val LocalPalavramentoColors = staticCompositionLocalOf { DarkPalavramentoColors }

/** Accessor mirroring `MaterialTheme.colorScheme`: `PalavramentoColors.current.tileBackground`. */
object PalavramentoColors {
  val current: PalavramentoColorPalette
    @Composable get() = LocalPalavramentoColors.current
}
