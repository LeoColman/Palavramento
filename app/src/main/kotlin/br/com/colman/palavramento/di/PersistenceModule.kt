// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.di

import android.content.Context
import android.os.SystemClock
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import br.com.colman.palavramento.data.AuthController
import br.com.colman.palavramento.data.Database
import br.com.colman.palavramento.data.HistoryRepository
import br.com.colman.palavramento.data.ProfileRepository
import br.com.colman.palavramento.data.SqlDelightHistoryRepository
import br.com.colman.palavramento.data.SqlDelightProfileRepository
import br.com.colman.palavramento.data.SyncService
import br.com.colman.palavramento.network.MultiplayerSession
import org.koin.dsl.module

/**
 * The local cache (dossier 7, ADR 0009) and the session layer built on top of it. Kept out of
 * [AppModule] on purpose, for the same reason [ViewModelModule] is: [AndroidSqliteDriver] opens a
 * real `SQLiteOpenHelper` against the framework's `android.jar`, which
 * [AppModuleTest][br.com.colman.palavramento.di.AppModuleTest]'s plain-JVM `checkModules()` run
 * cannot construct (no real Android runtime backs it there), same as a view model's `init` block
 * doing real I/O.
 */
val PersistenceModule = module {
  // No custom SupportSQLiteOpenHelper.Factory (unlike the reference project, Petals): the schema
  // only uses plain INTEGER/TEXT/REAL columns and basic DML, well within what the framework's own
  // bundled SQLite already supports from minSdk 26 up (ADR 0009).
  single<SqlDriver> { AndroidSqliteDriver(Database.Schema, get<Context>(), "palavramento.db") }
  single { Database(get()) }
  single<ProfileRepository> { SqlDelightProfileRepository(get()) }
  single<HistoryRepository> { SqlDelightHistoryRepository(get()) }

  single { AuthController(get(), get(), get(), get()) }
  single { SyncService(get(), get(), get(), get()) }

  factory {
    val authController = get<AuthController>()
    MultiplayerSession(
      transport = get(),
      accessTokenProvider = { authController.validAccessToken() },
      elapsedRealtimeMs = { SystemClock.elapsedRealtime() },
    )
  }
}
