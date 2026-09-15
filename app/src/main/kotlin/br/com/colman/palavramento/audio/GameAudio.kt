// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.audio

/**
 * Boundary to the platform's audio playback (task brief: "an audio interface... with the Android
 * implementation behind Koin, so view models and tests use a fake"): the round's looping background
 * music, sped up as time runs out ([updateRemaining], see [MusicSpeedCurve]), and the three short
 * accept/reject sound effects ([play]). [AndroidGameAudio] is the real implementation (`MediaPlayer`
 * for the music, `SoundPool` for low-latency effects); tests use a fake instead, since none of this
 * runs on the plain JVM.
 */
interface GameAudio {

  /**
   * Starts the looping background track for [roundId]. A no-op when [roundId] is already the one
   * playing: a reconnect that hands back a fresh `RoundStart` for the same round must not restart
   * the track from zero (see [RoomAudioPolicy.musicAction]).
   */
  fun startMusic(roundId: String)

  /** Feeds the milliseconds left in the round into [MusicSpeedCurve] to update playback speed/pitch. */
  fun updateRemaining(remainingMs: Long)

  /** Stops and releases the music track (round end, leaving the room, app backgrounded). Idempotent. */
  fun stopMusic()

  /** Plays one short sound effect, independent of the music track (low latency, overlaps are fine). */
  fun play(effect: SoundEffect)

  /** Releases every player/pool held by this instance. Safe to call more than once. */
  fun release()
}

/**
 * The three short sound effects: a mapped [br.com.colman.palavramento.state.SubmissionFeedback],
 * see [toSoundEffect].
 */
enum class SoundEffect {
  /** Bright, rising: a new word was accepted. */
  Accepted,

  /** Low, short: the submitted path was not a valid word (or too short, blocked, etc). */
  Rejected,

  /** Neutral, softer: a word the player had already found this round (the yellow flash). */
  AlreadyFound,
}
