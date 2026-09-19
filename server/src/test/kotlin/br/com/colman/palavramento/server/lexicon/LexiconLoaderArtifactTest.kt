// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import br.com.colman.palavramento.domain.lexicon.lookup
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * What [LexiconLoader] itself promises: it finds the artifact the build packaged and hands back a
 * usable lexicon. Deliberately small, and deliberately free of `forms.tsv` and of any timing
 * assertion, so it is the spec the mutation run can use (ADR 0015). [LexiconLoaderTest] stays the
 * acceptance test for the artifact's *contents*, against the whole of `forms.tsv`.
 */
class LexiconLoaderArtifactTest : FunSpec({
  test("The artifact is packaged exactly where the build says it is") {
    LexiconResourcePath shouldBe "/lexicon/pt-BR.bin"

    LexiconLoader.javaClass.getResourceAsStream(LexiconResourcePath).shouldNotBeNull().close()
  }

  test("The loaded lexicon answers for real pt-BR words and refuses a non-word") {
    val lexicon = LexiconLoader.load()

    lexicon.lookup("PAIS").shouldNotBeNull().display shouldBe "pais"
    lexicon.lookup("CASA").shouldNotBeNull()
    lexicon.lookup("QQQQQQ").shouldBeNull()
  }

  test("Loading twice gives two independent, equally usable lexicons") {
    val first = LexiconLoader.load()
    val second = LexiconLoader.load()

    first.lookup("PAIS") shouldBe second.lookup("PAIS")
  }
})
