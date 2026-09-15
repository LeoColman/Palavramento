// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import androidx.annotation.StringRes
import br.com.colman.palavramento.R
import br.com.colman.palavramento.domain.submission.RejectionReason

/** pt-BR message resource for a [RejectionReason] (dossie 6.2 rejection feedback). */
@StringRes
fun RejectionReason.messageRes(): Int = when (this) {
  RejectionReason.NotAWord -> R.string.match_reason_not_a_word
  RejectionReason.AlreadyFound -> R.string.match_reason_already_found
  RejectionReason.InvalidPath -> R.string.match_reason_invalid_path
  RejectionReason.TooShort -> R.string.match_reason_too_short
}
