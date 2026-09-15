// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.audio

/** In-memory [GameAudio] for [br.com.colman.palavramento.ui.room.RoomViewModelTest]: records every call. */
class FakeGameAudio : GameAudio {

  val startedRoundIds = mutableListOf<String>()
  val remainingUpdates = mutableListOf<Long>()
  val playedEffects = mutableListOf<SoundEffect>()
  var stopCount = 0
    private set
  var releaseCount = 0
    private set

  override fun startMusic(roundId: String) {
    startedRoundIds += roundId
  }

  override fun updateRemaining(remainingMs: Long) {
    remainingUpdates += remainingMs
  }

  override fun stopMusic() {
    stopCount++
  }

  override fun play(effect: SoundEffect) {
    playedEffects += effect
  }

  override fun release() {
    releaseCount++
  }
}
