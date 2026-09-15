// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.audio

import br.com.colman.palavramento.domain.submission.RejectionReason
import br.com.colman.palavramento.state.SubmissionFeedback

/**
 * Maps a submission's [SubmissionFeedback] to the [SoundEffect] that should play for it (task
 * brief: "one for an accepted word... one for a rejected word... one for a word the player had
 * already found"). [RejectionReason.AlreadyFound] gets its own, softer [SoundEffect.AlreadyFound]
 * instead of [SoundEffect.Rejected]: it is the sound counterpart of the yellow "duplicate" flash
 * ([br.com.colman.palavramento.ui.room.BoardView]'s `TileFlashKind.Duplicate`), not the red one - a
 * valid word, just not new, so it should not read as a mistake.
 */
fun SubmissionFeedback.toSoundEffect(): SoundEffect = when (this) {
  is SubmissionFeedback.Accepted -> SoundEffect.Accepted
  is SubmissionFeedback.Rejected ->
    if (reason == RejectionReason.AlreadyFound) SoundEffect.AlreadyFound else SoundEffect.Rejected
}
