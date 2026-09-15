// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [SettingsRepository] backed by its own Preferences DataStore, separate from auth's. */
class DataStoreSettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsRepository {

  override val hapticsEnabled: Flow<Boolean> = booleanPreference(HapticsEnabledKey)
  override val musicEnabled: Flow<Boolean> = booleanPreference(MusicEnabledKey)
  override val effectsEnabled: Flow<Boolean> = booleanPreference(EffectsEnabledKey)

  override suspend fun setHapticsEnabled(enabled: Boolean) = setPreference(HapticsEnabledKey, enabled)

  override suspend fun setMusicEnabled(enabled: Boolean) = setPreference(MusicEnabledKey, enabled)

  override suspend fun setEffectsEnabled(enabled: Boolean) = setPreference(EffectsEnabledKey, enabled)

  private fun booleanPreference(key: Preferences.Key<Boolean>): Flow<Boolean> =
    dataStore.data.map { preferences -> preferences[key] ?: DefaultEnabled }

  private suspend fun setPreference(key: Preferences.Key<Boolean>, enabled: Boolean) {
    dataStore.edit { preferences -> preferences[key] = enabled }
  }

  private companion object {
    val HapticsEnabledKey: Preferences.Key<Boolean> = booleanPreferencesKey("haptics_enabled")
    val MusicEnabledKey: Preferences.Key<Boolean> = booleanPreferencesKey("music_enabled")
    val EffectsEnabledKey: Preferences.Key<Boolean> = booleanPreferencesKey("effects_enabled")
    const val DefaultEnabled = true
  }
}
