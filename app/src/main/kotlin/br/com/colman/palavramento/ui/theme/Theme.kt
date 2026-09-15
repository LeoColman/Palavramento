// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.theme

import android.animation.ValueAnimator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import br.com.colman.palavramento.ui.common.LocalAnimationsEnabled

/**
 * App-wide theme. Dark only (dossier 6.5: "Suporte a tema claro fora do escopo"): the palette is
 * not derived from `isSystemInDarkTheme()`.
 */
@Composable
fun PalavramentoTheme(content: @Composable () -> Unit) {
  val palette = DarkPalavramentoColors
  // Read once per composition root: ValueAnimator.areAnimatorsEnabled() reflects the system
  // "remove animations" setting (task brief 2), and every phase-6 animation reads it through
  // LocalAnimationsEnabled instead of querying Android directly.
  val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
  CompositionLocalProvider(
    LocalPalavramentoColors provides palette,
    LocalAnimationsEnabled provides animationsEnabled,
  ) {
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
