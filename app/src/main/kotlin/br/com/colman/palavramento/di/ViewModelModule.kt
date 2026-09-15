// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.di

import br.com.colman.palavramento.ui.lobby.LobbyViewModel
import br.com.colman.palavramento.ui.room.RoomViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * View models, split out from [AppModule] (see its KDoc): both [LobbyViewModel] and
 * [RoomViewModel] start real network I/O from their `init` block, which only makes sense wired to
 * an actual Android `ViewModelStoreOwner`/main dispatcher, not to a plain JVM `checkModules()` run.
 */
val ViewModelModule = module {
  viewModel { LobbyViewModel(get(), get()) }
  viewModel { RoomViewModel(get()) }
}
