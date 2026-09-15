// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.settings

import kotlinx.coroutines.flow.Flow

/**
 * Persists device-local, per-player-device preferences (task brief 3, and the audio task brief's
 * "Música"/"Efeitos sonoros" toggles): today, haptics and the two audio toggles. Deliberately its own
 * small repository, not folded into [br.com.colman.palavramento.data.TokenRepository]: that one holds
 * authentication state owned by phase 5's login/promotion work, while this is pure UI preference with
 * nothing to migrate or sync.
 */
interface SettingsRepository {

  /** Whether haptic feedback (tile append tick, accept/reject) is on. Defaults to true. */
  val hapticsEnabled: Flow<Boolean>

  /** Whether the background music (audio task brief) plays during a round. Defaults to true. */
  val musicEnabled: Flow<Boolean>

  /** Whether the accept/reject/already-found sound effects (audio task brief) play. Defaults to true. */
  val effectsEnabled: Flow<Boolean>

  suspend fun setHapticsEnabled(enabled: Boolean)

  suspend fun setMusicEnabled(enabled: Boolean)

  suspend fun setEffectsEnabled(enabled: Boolean)
}
