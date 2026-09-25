// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.palavramento.audio.GameAudio
import br.com.colman.palavramento.audio.MusicSpeedCurve
import br.com.colman.palavramento.audio.RoomAudioPolicy
import br.com.colman.palavramento.clock.ServerClock
import br.com.colman.palavramento.data.SyncService
import br.com.colman.palavramento.network.ConnectionStatus
import br.com.colman.palavramento.network.MultiplayerSession
import br.com.colman.palavramento.settings.SettingsRepository
import br.com.colman.palavramento.state.MatchUiState
import br.com.colman.palavramento.state.SubmissionFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns the room's [MultiplayerSession] for as long as this screen is on the back stack: starts the
 * connect/handshake/reconnect loop when created, exposes its [state], [clock], [connectionStatus]
 * and [connectionAttempts], and leaves the room (and stops reconnecting) when the player navigates
 * back.
 *
 * [pause]/[resume] let [RoomScreen] additionally disconnect/reconnect on app background/foreground
 * (task brief 5) without leaking coroutines: [pause] cancels the loop job it started instead of
 * relying only on [MultiplayerSession.stop], which would leave a `collect` suspended on an open
 * socket until the next natural drop.
 *
 * Task brief 2 (sync policy): every time [state] reaches a new [MatchUiState.PostRound] (a fresh
 * `RoundEnd`, tracked by `roundId` so a `Leaderboard` update on the *same* round does not resync
 * again), [syncService] refreshes the local cache - the same trigger the lobby uses on open.
 *
 * Audio task brief: this is also where the background music and sound effects are driven from,
 * folding every [state] update through [RoomAudioPolicy] (pure logic, see its KDoc) into calls on
 * [gameAudio] - not [MatchScreen], so the track's lifetime is tied to the room, not to whether the
 * match screen happens to be composed. [musicEnabled]/[effectsEnabled] gate those calls the same way
 * [br.com.colman.palavramento.ui.settings.MatchSettingsSheet] gates haptics, and both are followed
 * for as long as the room lives, not only sampled at a round transition: the music toggle is applied
 * to the track already playing (see [setMusicPlaying]) and the effects one is re-read at every
 * sound, so flipping either is heard at once, with no restart. [updateMusicSpeed] is called from
 * [MatchScreen]'s own countdown tick ([br.com.colman.palavramento.ui.common.rememberRemainingMs]),
 * reusing that existing ticker instead of a second one here.
 */
class RoomViewModel(
  private val session: MultiplayerSession,
  private val syncService: SyncService,
  private val gameAudio: GameAudio,
  private val settingsRepository: SettingsRepository,
) : ViewModel() {

  val state: StateFlow<MatchUiState> = session.state
  val clock: StateFlow<ServerClock?> = session.clock
  val connectionStatus: StateFlow<ConnectionStatus> = session.connectionStatus

  /** Task brief 4 (orchestrator finding): lets [RoomScreen] show an error+retry state, not an infinite spinner. */
  val connectionAttempts: StateFlow<Int> = session.connectionAttempts

  // Eagerly, not WhileSubscribed: these gate audio decisions made inside the state collector below,
  // not something a composable collects, so they must stay live for the whole view model lifetime.
  private val musicEnabled = settingsRepository.musicEnabled
    .stateIn(viewModelScope, SharingStarted.Eagerly, true)
  private val effectsEnabled = settingsRepository.effectsEnabled
    .stateIn(viewModelScope, SharingStarted.Eagerly, true)

  private var runJob: Job? = null
  private var lastSyncedRoundId: String? = null
  private var musicRoundId: String? = null
  private var lastPlayedFeedback: SubmissionFeedback? = null
  private var lastMusicSpeed: Float? = null

  // Whether [gameAudio] was actually asked to play: [musicRoundId] cannot answer that, since it is
  // also set for a round whose track the music toggle is keeping silent.
  private var musicTrackPlaying = false

  init {
    start()
    viewModelScope.launch {
      session.state.collect { current ->
        if (current is MatchUiState.PostRound && current.roundId != lastSyncedRoundId) {
          lastSyncedRoundId = current.roundId
          syncService.sync()
        }
        handleMusicTransition(current)
        handleFeedbackSound(current)
      }
    }
    // The music toggle has to be heard the moment it is flipped. The track outlives any single state
    // update, so nothing else would revisit the decision before the next round: a sound effect is
    // gated again at every [handleFeedbackSound], but the music is decided once and then just plays.
    viewModelScope.launch { musicEnabled.collect(::setMusicPlaying) }
  }

  /** (Re)starts the connect/handshake/reconnect loop; a no-op while it is already running. */
  fun start() {
    if (runJob?.isActive == true) return
    runJob = viewModelScope.launch { session.run() }
  }

  /**
   * Alias for [start], for the app-foreground lifecycle event. Also re-evaluates the music: [pause]
   * stopped it unconditionally, and the room's [state] can still be the same [MatchUiState.InRound]
   * as before backgrounding (nothing new has arrived over the socket yet), so nothing would
   * otherwise fire [handleMusicTransition] again for it.
   */
  fun resume() {
    start()
    handleMusicTransition(session.state.value)
  }

  /** Cancels the loop, closes the current connection, and stops the music (task brief: app background). */
  fun pause() {
    runJob?.cancel()
    viewModelScope.launch { session.disconnect() }
    setMusicPlaying(false)
    musicRoundId = null
  }

  /** Task brief 4: manual "Tentar novamente" after the first connection keeps failing. */
  fun retryConnection() {
    pause()
    start()
  }

  fun submitWord(roundId: String, path: List<Int>, clientTimestampMs: Long) {
    viewModelScope.launch { runCatching { session.submitWord(roundId, path, clientTimestampMs) } }
  }

  /**
   * Feeds the milliseconds left in the round into [MusicSpeedCurve], skipping the call to
   * [gameAudio] entirely when the resulting speed has not changed since the last tick - most ticks,
   * outside the ramp window, would otherwise reapply the exact same [android.media.PlaybackParams].
   * Called from [MatchScreen]'s countdown tick, so it only ever runs while a round is showing.
   */
  fun updateMusicSpeed(remainingMs: Long) {
    val speed = MusicSpeedCurve.speedFor(remainingMs)
    if (speed == lastMusicSpeed) return
    lastMusicSpeed = speed
    gameAudio.updateRemaining(remainingMs)
  }

  fun leaveRoom() {
    session.stop()
    runJob?.cancel()
    setMusicPlaying(false)
    musicRoundId = null
    viewModelScope.launch {
      runCatching { session.leaveRoom() }
      session.disconnect()
    }
  }

  override fun onCleared() {
    session.stop()
    runJob?.cancel()
    gameAudio.release()
  }

  private fun handleMusicTransition(state: MatchUiState) {
    when (val action = RoomAudioPolicy.musicAction(musicRoundId, state)) {
      is RoomAudioPolicy.MusicAction.Start -> {
        musicRoundId = action.roundId
        setMusicPlaying(musicEnabled.value)
      }

      RoomAudioPolicy.MusicAction.Stop -> {
        musicRoundId = null
        setMusicPlaying(false)
      }

      RoomAudioPolicy.MusicAction.None -> Unit
    }
  }

  /**
   * Starts or stops the track for the round in progress: the single place a round transition, a flip
   * of the music toggle and [pause]/[leaveRoom] all go through, so turning the music off is heard
   * right away and turning it back on picks the current round up again instead of the next one.
   *
   * Stopping is a no-op while nothing is playing ([musicTrackPlaying]), so a toggle flipped between
   * rounds - or one that was already off when the round started - never fakes a stop.
   */
  private fun setMusicPlaying(shouldPlay: Boolean) {
    val roundId = musicRoundId
    if (shouldPlay && roundId != null) {
      musicTrackPlaying = true
      // A track that (re)starts always plays at normal speed, so the next countdown tick has to
      // reapply the ramp even when it lands on the very speed [updateMusicSpeed] last sent.
      lastMusicSpeed = null
      gameAudio.startMusic(roundId)
    } else if (musicTrackPlaying) {
      musicTrackPlaying = false
      gameAudio.stopMusic()
    }
  }

  private fun handleFeedbackSound(state: MatchUiState) {
    if (state !is MatchUiState.InRound) return
    val effect = RoomAudioPolicy.effectAction(lastPlayedFeedback, state) ?: return
    lastPlayedFeedback = state.lastFeedback
    if (effectsEnabled.value) gameAudio.play(effect)
  }
}
