// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.submission

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Path
import br.com.colman.palavramento.domain.board.isValidOn
import br.com.colman.palavramento.domain.board.spell
import br.com.colman.palavramento.domain.lexicon.Lexicon
import br.com.colman.palavramento.domain.lexicon.lookup
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.mutator.blocks
import br.com.colman.palavramento.domain.mutator.effectiveValueOf
import br.com.colman.palavramento.domain.mutator.minimumLength

/**
 * Validates one client submission end to end (dossier 5.2): the server is the only source of
 * truth for word validity and scoring, so this is what a `SubmitWord` handler calls.
 *
 * Checks run in a fixed order, each short-circuiting the rest, so a caller always gets the most
 * relevant single reason:
 * 1. **Path validity** ([RejectionReason.InvalidPath]): in bounds, adjacent steps, no tile reused.
 *    Checked first because every other check needs a real word to look at.
 * 2. **Length** ([RejectionReason.TooShort]): below 3 letters, or the mutator's minimum.
 * 3. **Mutator** ([RejectionReason.BlockedByMutator]): the word contains a forbidden letter.
 * 4. **Lexicon** ([RejectionReason.NotAWord]): the normalized word is not in the dictionary.
 * 5. **Duplicate** ([RejectionReason.AlreadyFound]): checked last, and by normalized word regardless
 *    of path, so a word already scored some other way cannot score again through a new path.
 */
object SubmissionValidator {

  // Five guard clauses plus the final Accepted return is the documented check order above: turning
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
    if (normalized.length < mutator.minimumLength()) return SubmissionResult.Rejected(RejectionReason.TooShort)

    if (mutator.blocks(normalized)) return SubmissionResult.Rejected(RejectionReason.BlockedByMutator)

    val entry = lexicon.lookup(normalized) ?: return SubmissionResult.Rejected(RejectionReason.NotAWord)

    if (normalized in alreadyFound) return SubmissionResult.Rejected(RejectionReason.AlreadyFound)

    val score = path.indices.sumOf { mutator.effectiveValueOf(board.tiles[it]) }
    return SubmissionResult.Accepted(entry.display, score)
  }
}
