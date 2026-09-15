// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.submission

/** Outcome of validating one submitted path (dossier 5.2). */
sealed interface SubmissionResult {
  /** [word] is the lexicon's display form, [score] the sum of the path's effective tile values. */
  data class Accepted(val word: String, val score: Int) : SubmissionResult

  data class Rejected(val reason: RejectionReason) : SubmissionResult
}
