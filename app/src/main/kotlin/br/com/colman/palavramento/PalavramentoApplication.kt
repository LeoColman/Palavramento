// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento

import android.app.Application
import br.com.colman.palavramento.di.AppModule
import br.com.colman.palavramento.di.PersistenceModule
import br.com.colman.palavramento.di.ViewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PalavramentoApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    startKoin {
      androidContext(this@PalavramentoApplication)
      modules(AppModule, PersistenceModule, ViewModelModule)
    }
  }
}
