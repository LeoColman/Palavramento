// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.di

import android.content.Context
import android.os.SystemClock
import androidx.datastore.preferences.preferencesDataStore
import br.com.colman.palavramento.BuildConfig
import br.com.colman.palavramento.data.DataStoreTokenRepository
import br.com.colman.palavramento.data.TokenRepository
import br.com.colman.palavramento.network.HttpClientFactory
import br.com.colman.palavramento.network.KtorMultiplayerTransport
import br.com.colman.palavramento.network.KtorRestApi
import br.com.colman.palavramento.network.MultiplayerSession
import br.com.colman.palavramento.network.MultiplayerTransport
import br.com.colman.palavramento.network.RestApi
import kotlinx.coroutines.flow.first
import org.koin.dsl.bind
import org.koin.dsl.module

private val Context.authDataStore by preferencesDataStore("auth")

/**
 * Wires networking and persistence (task brief: Koin, `koin-android`). Kept apart from
 * [ViewModelModule] on purpose: [AppModuleTest][br.com.colman.palavramento.di.AppModuleTest] runs
 * `checkModules()` against this module alone on the JVM, and a view model's `init` block doing real
 * I/O (see [ViewModelModule]) is not something that check can safely construct outside Android.
 */
val AppModule = module {
  single { HttpClientFactory.create() }
  single<RestApi> { KtorRestApi(get(), BuildConfig.SERVER_URL) }
  single<TokenRepository> { DataStoreTokenRepository(get<Context>().authDataStore) }

  factory { KtorMultiplayerTransport(get(), BuildConfig.SERVER_URL) } bind MultiplayerTransport::class

  factory {
    val tokenRepository = get<TokenRepository>()
    MultiplayerSession(
      transport = get(),
      accessTokenProvider = { tokenRepository.tokens.first()?.accessToken },
      elapsedRealtimeMs = { SystemClock.elapsedRealtime() },
    )
  }
}
