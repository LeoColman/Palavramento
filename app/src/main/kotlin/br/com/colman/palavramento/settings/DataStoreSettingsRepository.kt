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

  override val hapticsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
    preferences[HapticsEnabledKey] ?: DefaultHapticsEnabled
  }

  override suspend fun setHapticsEnabled(enabled: Boolean) {
    dataStore.edit { preferences -> preferences[HapticsEnabledKey] = enabled }
  }

  private companion object {
    val HapticsEnabledKey: Preferences.Key<Boolean> = booleanPreferencesKey("haptics_enabled")
    const val DefaultHapticsEnabled = true
  }
}
