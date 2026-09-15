// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * App-wide theme. Dark only (dossier 6.5: "Suporte a tema claro fora do escopo"): the palette is
 * not derived from `isSystemInDarkTheme()`.
 */
@Composable
fun PalavramentoTheme(content: @Composable () -> Unit) {
  val palette = DarkPalavramentoColors
  CompositionLocalProvider(LocalPalavramentoColors provides palette) {
    MaterialTheme(
      colorScheme = darkColorScheme(
        background = palette.background,
        surface = palette.surface,
        surfaceVariant = palette.surfaceVariant,
        primary = palette.matchAccent,
        secondary = palette.resultsAccent,
        onBackground = palette.textPrimary,
        onSurface = palette.textPrimary,
        onPrimary = palette.textPrimary,
        onSecondary = palette.textPrimary,
      ),
      content = content,
    )
  }
}
