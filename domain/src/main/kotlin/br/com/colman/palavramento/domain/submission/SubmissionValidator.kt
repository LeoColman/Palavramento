// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.submission

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Path
import br.com.colman.palavramento.domain.board.isValidOn
import br.com.colman.palavramento.domain.board.spell
import br.com.colman.palavramento.domain.lexicon.Lexicon
import br.com.colman.palavramento.domain.lexicon.lookup
import br.com.colman.palavramento.domain.mutator.DefaultMinimumLength
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.mutator.effectiveValueOf

/**
 * Validates one client submission end to end (dossier 5.2): the server is the only source of
 * truth for word validity and scoring, so this is what a `SubmitWord` handler calls.
 *
 * Checks run in a fixed order, each short-circuiting the rest, so a caller always gets the most
 * relevant single reason:
 * 1. **Path validity** ([RejectionReason.InvalidPath]): in bounds, adjacent steps, no tile reused.
 *    Checked first because every other check needs a real word to look at.
 * 2. **Length** ([RejectionReason.TooShort]): below [DefaultMinimumLength] letters (ADR 0012: no
 *    mutator overrides this any more).
 * 3. **Lexicon** ([RejectionReason.NotAWord]): the normalized word is not in the dictionary.
 * 4. **Duplicate** ([RejectionReason.AlreadyFound]): checked last, and by normalized word regardless
 *    of path, so a word already scored some other way cannot score again through a new path.
 */
object SubmissionValidator {

  // Four guard clauses plus the final Accepted return is the documented check order above: turning
  // this into a single expression would make that order much harder to read, not easier.
  @Suppress("ReturnCount")
  fun validate(
    board: Board,
    mutator: Mutator,
    lexicon: Lexicon,
    alreadyFound: Set<String>,
    path: Path,
  ): SubmissionResult {
    if (!path.isValidOn(board)) return SubmissionResult.Rejected(RejectionReason.InvalidPath)

    val normalized = path.spell(board)
    if (normalized.length < DefaultMinimumLength) return SubmissionResult.Rejected(RejectionReason.TooShort)

    val entry = lexicon.lookup(normalized) ?: return SubmissionResult.Rejected(RejectionReason.NotAWord)

    if (normalized in alreadyFound) return SubmissionResult.Rejected(RejectionReason.AlreadyFound)

    val score = path.indices.sumOf { mutator.effectiveValueOf(board.tiles[it]) }
    return SubmissionResult.Accepted(entry.display, score)
  }
}
