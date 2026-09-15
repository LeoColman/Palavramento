// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the system "remove animations" accessibility setting (Settings > Accessibility >
 * Remove animations, which zeroes the animator duration scale) is off. Compose's own
 * [androidx.compose.animation.core.AnimationSpec]-driven APIs do not consult that setting
 * automatically (task brief 2: "Respect the system 'remove animations' setting where Compose makes
 * it easy"), so every animation added in phase 6 (tile trace/flash, rotation, flip countdown) reads
 * this local and swaps in a zero-duration spec when it is false, instead of reimplementing motion
 * detection per call site.
 *
 * [br.com.colman.palavramento.ui.theme.PalavramentoTheme] provides the real value, read once from
 * `ValueAnimator.areAnimatorsEnabled()`; the default here (true) only matters for previews/tests that
 * do not go through the theme.
 */
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

/** [baseMillis] when system animations are enabled, or 0 (an instant jump) when they are not. */
@Composable
fun animationDurationMillis(baseMillis: Int): Int = if (LocalAnimationsEnabled.current) baseMillis else 0
