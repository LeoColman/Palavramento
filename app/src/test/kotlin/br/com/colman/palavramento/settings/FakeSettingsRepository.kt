// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [SettingsRepository] for view model tests: all three toggles default to on. */
class FakeSettingsRepository(
  hapticsEnabled: Boolean = true,
  musicEnabled: Boolean = true,
  effectsEnabled: Boolean = true,
) : SettingsRepository {

  private val hapticsState = MutableStateFlow(hapticsEnabled)
  private val musicState = MutableStateFlow(musicEnabled)
  private val effectsState = MutableStateFlow(effectsEnabled)

  override val hapticsEnabled: Flow<Boolean> = hapticsState
  override val musicEnabled: Flow<Boolean> = musicState
  override val effectsEnabled: Flow<Boolean> = effectsState

  override suspend fun setHapticsEnabled(enabled: Boolean) {
    hapticsState.value = enabled
  }

  override suspend fun setMusicEnabled(enabled: Boolean) {
    musicState.value = enabled
  }

  override suspend fun setEffectsEnabled(enabled: Boolean) {
    effectsState.value = enabled
  }
}
