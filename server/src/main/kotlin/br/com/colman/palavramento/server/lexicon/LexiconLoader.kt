// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import br.com.colman.palavramento.domain.lexicon.TrieLexicon

/** Classpath location `compileLexicon` (`server/build.gradle.kts`) packages the artifact at. */
const val LexiconResourcePath = "/lexicon/pt-BR.bin"

/**
 * Loads the pt-BR lexicon built at compile time (dossier §11 phase 1, ADR 0004) from the server
 * jar's classpath. No Koin wiring yet: that is phase 3's job, once `:server` has a DI graph to wire
 * it into.
 */
object LexiconLoader {
  fun load(): TrieLexicon {
    val stream = checkNotNull(javaClass.getResourceAsStream(LexiconResourcePath)) {
      "Lexicon artifact not found at $LexiconResourcePath. Was compileLexicon run before processResources?"
    }
    return stream.use { TrieLexicon.read(it) }
  }
}
