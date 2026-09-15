// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.di

import android.content.Context
import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.koin.test.check.checkModules

/**
 * Verifies every [AppModule] definition can actually be resolved (a mistyped `get<T>()` inside a
 * factory only fails at first real use otherwise). A throwaway container, not the app's own Koin
 * instance, since `checkModules` closes whatever it checks.
 */
class AppModuleTest : FunSpec({

  test("AppModule resolves end to end") {
    val application = koinApplication {
      androidContext(mockk<Context>(relaxed = true))
      modules(AppModule)
    }

    try {
      application.checkModules()
    } finally {
      application.close()
    }
  }
})
