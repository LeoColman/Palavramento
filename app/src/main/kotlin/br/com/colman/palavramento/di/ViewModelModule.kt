// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.di

import br.com.colman.palavramento.ui.auth.LoginViewModel
import br.com.colman.palavramento.ui.history.HistoryViewModel
import br.com.colman.palavramento.ui.lobby.LobbyViewModel
import br.com.colman.palavramento.ui.room.RoomViewModel
import br.com.colman.palavramento.ui.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * View models, split out from [AppModule] (see its KDoc): [LobbyViewModel] and [RoomViewModel]
 * start real network I/O from their `init` block, which only makes sense wired to an actual Android
 * `ViewModelStoreOwner`/main dispatcher, not to a plain JVM `checkModules()` run. [SettingsViewModel],
 * [LoginViewModel] and [HistoryViewModel] do no I/O in `init` but live here anyway to keep every view
 * model in one place; they still depend on [PersistenceModule] types (`AuthController`,
 * `HistoryRepository`), which is another reason this module is never the target of `checkModules()`.
 */
val ViewModelModule = module {
  viewModel { LobbyViewModel(get(), get(), get()) }
  viewModel { RoomViewModel(get(), get()) }
  viewModel { SettingsViewModel(get()) }
  viewModel { LoginViewModel(get()) }
  viewModel { HistoryViewModel(get()) }
}
