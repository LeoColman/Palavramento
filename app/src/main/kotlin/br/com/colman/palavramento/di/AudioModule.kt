// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.di

import br.com.colman.palavramento.audio.AndroidGameAudio
import br.com.colman.palavramento.audio.GameAudio
import org.koin.dsl.module

/**
 * [GameAudio]'s real implementation (task brief: "the Android implementation behind Koin"). Kept out
 * of [AppModule] for the same reason [PersistenceModule] is (see its KDoc): [AndroidGameAudio] builds
 * a real `SoundPool`/`MediaPlayer` in its constructor, Android framework classes that
 * [AppModuleTest][br.com.colman.palavramento.di.AppModuleTest]'s plain-JVM `checkModules()` cannot
 * construct. `factory`, not `single`, so a fresh player set is created - and released, see
 * [br.com.colman.palavramento.ui.room.RoomViewModel.onCleared] - every time a room is entered, the
 * same lifecycle [PersistenceModule]'s `MultiplayerSession` factory already uses.
 */
val AudioModule = module {
  factory<GameAudio> { AndroidGameAudio(get()) }
}
