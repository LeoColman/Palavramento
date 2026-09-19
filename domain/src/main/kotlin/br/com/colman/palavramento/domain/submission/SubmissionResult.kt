// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.submission

/** Outcome of validating one submitted path (dossier 5.2). */
sealed interface SubmissionResult {
  /**
   * [normalized] is the exact spelling [SubmissionValidator] accepted, out of a path's possibly
   * several [br.com.colman.palavramento.domain.board.spellings] (ADR 0015: a path over an
   * alternatives tile can spell more than one word). [word] is that spelling's lexicon display form,
   * [score] the sum of the path's effective tile values.
   *
   * Callers that record a found word (`RoundState` on the server, `OptimisticSubmission` in the app)
   * must key on [normalized], not re-derive it by re-spelling the path: re-spelling a path with an
   * alternatives tile on it is ambiguous, since the same path can legitimately spell more than one
   * word, and only [normalized] says which one this particular submission actually matched.
   */
  data class Accepted(val normalized: String, val word: String, val score: Int) : SubmissionResult

  data class Rejected(val reason: RejectionReason) : SubmissionResult
}
