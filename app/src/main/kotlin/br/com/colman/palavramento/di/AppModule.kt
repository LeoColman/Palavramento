// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import br.com.colman.palavramento.BuildConfig
import br.com.colman.palavramento.data.DataStoreTokenRepository
import br.com.colman.palavramento.data.TokenRepository
import br.com.colman.palavramento.network.HttpClientFactory
import br.com.colman.palavramento.network.KtorMultiplayerTransport
import br.com.colman.palavramento.network.KtorRestApi
import br.com.colman.palavramento.network.MultiplayerTransport
import br.com.colman.palavramento.network.RestApi
import br.com.colman.palavramento.settings.DataStoreSettingsRepository
import br.com.colman.palavramento.settings.SettingsRepository
import org.koin.dsl.bind
import org.koin.dsl.module

private val Context.authDataStore by preferencesDataStore("auth")

// Its own file (task brief 3: "not in data/TokenRepository"), so a haptics toggle never collides
// with the auth DataStore's file or gets caught up in phase 5's login/promotion changes there.
private val Context.settingsDataStore by preferencesDataStore("settings")

/**
 * Wires networking and persistence (task brief: Koin, `koin-android`). Kept apart from
 * [ViewModelModule] and [PersistenceModule] on purpose: [AppModuleTest][br.com.colman.palavramento.di.AppModuleTest]
 * runs `checkModules()` against this module alone on the JVM. A view model's `init` block doing real
 * I/O (see [ViewModelModule]) and the SQLDelight driver's real `SQLiteOpenHelper` (see
 * [PersistenceModule], ADR 0009) are both things that check cannot safely construct outside Android.
 */
val AppModule = module {
  single { HttpClientFactory.create() }
  single<RestApi> { KtorRestApi(get(), BuildConfig.SERVER_URL) }
  single<TokenRepository> { DataStoreTokenRepository(get<Context>().authDataStore) }
  single<SettingsRepository> { DataStoreSettingsRepository(get<Context>().settingsDataStore) }

  factory { KtorMultiplayerTransport(get(), BuildConfig.SERVER_URL) } bind MultiplayerTransport::class
}
