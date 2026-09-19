// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.submission

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Path
import br.com.colman.palavramento.domain.board.isValidOn
import br.com.colman.palavramento.domain.board.spellings
import br.com.colman.palavramento.domain.lexicon.Lexicon
import br.com.colman.palavramento.domain.lexicon.LexiconEntry
import br.com.colman.palavramento.domain.lexicon.lookup
import br.com.colman.palavramento.domain.mutator.DefaultMinimumLength

/**
 * Validates one client submission end to end (dossier 5.2): the server is the only source of
 * truth for word validity and scoring, so this is what a `SubmitWord` handler calls.
 *
 * A path can spell more than one word since ADR 0015 ("Uma ou outra"): [Path.spellings] returns the
 * cartesian product of every tile's options along the path, in option order. Checks run in a fixed
 * order, each short-circuiting the rest, so a caller always gets the most relevant single reason:
 * 1. **Path validity** ([RejectionReason.InvalidPath]): in bounds, adjacent steps, no tile reused.
 *    Checked first because every other check needs a real word to look at.
 * 2. **Length** ([RejectionReason.TooShort]): every spelling is below [DefaultMinimumLength] letters
 *    (ADR 0012: no mutator overrides this any more).
 * 3. **Lexicon** ([RejectionReason.NotAWord]): none of the long-enough spellings is in the
 *    dictionary.
 * 4. **Duplicate** ([RejectionReason.AlreadyFound]): every spelling that is a lexicon word was
 *    already found. Otherwise, the first word-spelling (in option order) not already found is what
 *    gets accepted: tracing the same path twice can legitimately score both an "A" word and an "F"
 *    word, one submission at a time.
 *
 * The score is the sum of the traced tiles' own values: the round's mutator is already baked into
 * the tiles by the generator, so only the traced copy of a valuable letter scores its inflated value,
 * and the alternatives tile scores the same [br.com.colman.palavramento.domain.generator.BoardGenerator.OneOrOtherValue]
 * regardless of which option a word used.
 */
object SubmissionValidator {

  // Four guard clauses plus the final Accepted return is the documented check order above: turning
  // this into a single expression would make that order much harder to read, not easier.
  @Suppress("ReturnCount")
  fun validate(
    board: Board,
    lexicon: Lexicon,
    alreadyFound: Set<String>,
    path: Path,
  ): SubmissionResult {
    if (!path.isValidOn(board)) return SubmissionResult.Rejected(RejectionReason.InvalidPath)

    val longEnough = path.spellings(board).filter { it.length >= DefaultMinimumLength }
    if (longEnough.isEmpty()) return SubmissionResult.Rejected(RejectionReason.TooShort)

    val wordSpellings: List<Pair<String, LexiconEntry>> = longEnough.mapNotNull { spelling ->
      lexicon.lookup(spelling)?.let { entry -> spelling to entry }
    }
    if (wordSpellings.isEmpty()) return SubmissionResult.Rejected(RejectionReason.NotAWord)

    val (normalized, entry) = wordSpellings.firstOrNull { (spelling, _) -> spelling !in alreadyFound }
      ?: return SubmissionResult.Rejected(RejectionReason.AlreadyFound)

    val score = path.indices.sumOf { board.tiles[it].value }
    return SubmissionResult.Accepted(normalized, entry.display, score)
  }
}
