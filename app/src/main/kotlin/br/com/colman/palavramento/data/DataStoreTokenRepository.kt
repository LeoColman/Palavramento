// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import br.com.colman.palavramento.domain.protocol.AuthTokens
import br.com.colman.palavramento.domain.protocol.PalavramentoJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [TokenRepository] backed by Preferences DataStore (task brief: "persisting them in DataStore is
 * fine"), storing the whole [AuthTokens] payload as one JSON blob under [TokensKey]. A single key
 * keeps this in step with [AuthTokens] gaining fields later without a migration.
 */
class DataStoreTokenRepository(private val dataStore: DataStore<Preferences>) : TokenRepository {

  override val tokens: Flow<AuthTokens?> = dataStore.data.map { preferences ->
    preferences[TokensKey]?.let { PalavramentoJson.decodeFromString(AuthTokens.serializer(), it) }
  }

  override suspend fun save(tokens: AuthTokens) {
    dataStore.edit { preferences ->
      preferences[TokensKey] = PalavramentoJson.encodeToString(AuthTokens.serializer(), tokens)
    }
  }

  override suspend fun clear() {
    dataStore.edit { preferences -> preferences.remove(TokensKey) }
  }

  private companion object {
    val TokensKey: Preferences.Key<String> = stringPreferencesKey("auth_tokens")
  }
}
