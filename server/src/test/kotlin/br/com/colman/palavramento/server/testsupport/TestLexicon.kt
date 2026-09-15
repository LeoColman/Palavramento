// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.testsupport

import br.com.colman.palavramento.domain.lexicon.TrieLexicon
import br.com.colman.palavramento.server.lexicon.LexiconLoader

/**
 * Loads the ~80 MB real lexicon artifact once for the whole `:server:test` JVM (dossier phase 3
 * task: "do not load the 80 MB lexicon more often than needed"), shared by every spec that needs a
 * real [br.com.colman.palavramento.domain.lexicon.Lexicon] (board generation, submission validation).
 */
object TestLexicon {
  val lexicon: TrieLexicon by lazy { LexiconLoader.load() }
}
