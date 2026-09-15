// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.lexicon

import br.com.colman.palavramento.domain.protocol.ValidWord

/**
 * Builds the exact [Lexicon] a client needs to validate a submission locally (ADR 0014
 * "validacao-otimista"): every [ValidWord] of the round's own solution, the same set
 * `RoundStart.validWords` carries. Feeding this into
 * [br.com.colman.palavramento.domain.submission.SubmissionValidator.validate] runs the identical
 * rules the server runs against the full lexicon, with zero duplicated logic.
 *
 * Entries are [LexiconEntry.Unranked]: [br.com.colman.palavramento.domain.solver.WordTier] never
 * enters the validator's decision, only lexicon membership does, so the client never needs a
 * frequency rank it was never sent.
 */
fun List<ValidWord>.toLexicon(): Lexicon = InMemoryLexicon(
  associate { it.normalized to LexiconEntry(it.display, LexiconEntry.Unranked) },
)
