// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.settings

import kotlinx.coroutines.flow.Flow

/**
 * Persists device-local, per-player-device preferences (task brief 3): today, just whether haptics
 * are on. Deliberately its own small repository, not folded into
 * [br.com.colman.palavramento.data.TokenRepository]: that one holds authentication state owned by
 * phase 5's login/promotion work, while this is pure UI preference with nothing to migrate or sync.
 */
interface SettingsRepository {

  /** Whether haptic feedback (tile append tick, accept/reject) is on. Defaults to true. */
  val hapticsEnabled: Flow<Boolean>

  suspend fun setHapticsEnabled(enabled: Boolean)
}
