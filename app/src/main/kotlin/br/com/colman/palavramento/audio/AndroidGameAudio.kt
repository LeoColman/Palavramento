// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.SoundPool
import br.com.colman.palavramento.R

/**
 * [GameAudio] over `android.media` (task brief: `MediaPlayer` for the looping background track,
 * since only it exposes [PlaybackParams] for the speed/pitch ramp; `SoundPool` for the three short,
 * low-latency sound effects). Both use [AudioAttributes.USAGE_GAME]; the music additionally requests
 * audio focus before playing and pauses on loss, resuming on regain, so a phone call or another
 * app's playback does not fight with it (task brief).
 *
 * A fresh instance is created per room (`factory` in `di/AudioModule.kt`) and released by
 * [br.com.colman.palavramento.ui.room.RoomViewModel.onCleared] - never a Koin `single`, so a leaked
 * `MediaPlayer`/`SoundPool` from one room cannot outlive it.
 */
class AndroidGameAudio(private val context: Context) : GameAudio {

  private val audioManager = context.getSystemService(AudioManager::class.java)
  private val soundPool = SoundPool.Builder()
    .setMaxStreams(MaxConcurrentEffects)
    .setAudioAttributes(EffectAttributes)
    .build()

  // Registered before any load() call below: SoundPool.load() can complete synchronously fast
  // enough that a listener attached afterwards would miss the very first callback.
  private val loadedSoundIds = mutableSetOf<Int>()

  init {
    soundPool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loadedSoundIds += sampleId }
  }

  private val effectSoundIds: Map<SoundEffect, Int> = mapOf(
    SoundEffect.Accepted to soundPool.load(context, R.raw.sfx_accepted, 1),
    SoundEffect.Rejected to soundPool.load(context, R.raw.sfx_rejected, 1),
    SoundEffect.AlreadyFound to soundPool.load(context, R.raw.sfx_already_found, 1),
  )

  private var musicPlayer: MediaPlayer? = null
  private var musicRoundId: String? = null
  private var focusRequest: AudioFocusRequest? = null

  // Whether the music *should* be audible right now, independent of a transient focus loss: lets
  // AUDIOFOCUS_GAIN resume playback only when the caller has not meanwhile called stopMusic().
  private var wantsToPlayMusic = false

  private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
    when (change) {
      AudioManager.AUDIOFOCUS_LOSS,
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
      -> runCatching { musicPlayer?.pause() }

      AudioManager.AUDIOFOCUS_GAIN -> if (wantsToPlayMusic) runCatching { musicPlayer?.start() }
    }
  }

  override fun startMusic(roundId: String) {
    val existingPlayer = musicPlayer
    if (roundId == musicRoundId && existingPlayer != null) {
      wantsToPlayMusic = true
      if (requestAudioFocus()) runCatching { existingPlayer.start() }
      return
    }
    releaseMusicPlayer()
    val player = MediaPlayer.create(context, R.raw.bg_music, MusicAttributes, audioManager.generateAudioSessionId())
      ?: return
    player.isLooping = true
    musicPlayer = player
    musicRoundId = roundId
    wantsToPlayMusic = true
    if (requestAudioFocus()) runCatching { player.start() }
  }

  override fun updateRemaining(remainingMs: Long) {
    val player = musicPlayer ?: return
    val speed = MusicSpeedCurve.speedFor(remainingMs)
    // Pitch rises together with tempo, on purpose (docs/adr/0011-audio.md): the classic arcade
    // "hurry up" effect, which reads as urgency at a glance even with the screen off, instead of a
    // constant-pitch time stretch that would only be noticeable by ear.
    runCatching { player.playbackParams = PlaybackParams().setSpeed(speed).setPitch(speed) }
  }

  override fun stopMusic() {
    wantsToPlayMusic = false
    releaseMusicPlayer()
    musicRoundId = null
    abandonAudioFocus()
  }

  override fun play(effect: SoundEffect) {
    val soundId = effectSoundIds[effect] ?: return
    if (soundId !in loadedSoundIds) return
    soundPool.play(soundId, EffectVolume, EffectVolume, EffectPriority, NoLoop, NormalRate)
  }

  override fun release() {
    stopMusic()
    soundPool.release()
  }

  private fun releaseMusicPlayer() {
    musicPlayer?.let { player ->
      runCatching { player.stop() }
      player.release()
    }
    musicPlayer = null
  }

  private fun requestAudioFocus(): Boolean {
    val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
      .setAudioAttributes(MusicAttributes)
      .setOnAudioFocusChangeListener(focusChangeListener)
      .build()
      .also { focusRequest = it }
    return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
  }

  private fun abandonAudioFocus() {
    focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
  }

  private companion object {
    const val MaxConcurrentEffects = 3
    const val EffectVolume = 0.8f
    const val EffectPriority = 1
    const val NoLoop = 0
    const val NormalRate = 1f

    val MusicAttributes: AudioAttributes = AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_GAME)
      .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
      .build()

    val EffectAttributes: AudioAttributes = AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_GAME)
      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
      .build()
  }
}
