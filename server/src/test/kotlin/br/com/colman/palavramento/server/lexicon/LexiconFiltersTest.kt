// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LexiconFiltersTest : FunSpec({
  test("Accepts a plain lowercase word") {
    LexiconFilters.isAcceptableCanonicalForm("casa") shouldBe true
  }

  test("Rejects a hyphenated compound") {
    LexiconFilters.isAcceptableCanonicalForm("guarda-chuva") shouldBe false
  }

  test("Rejects a straight or curly apostrophe") {
    LexiconFilters.isAcceptableCanonicalForm("d'água") shouldBe false
    LexiconFilters.isAcceptableCanonicalForm("d’água") shouldBe false
  }

  test("Rejects an abbreviation with a dot") {
    LexiconFilters.isAcceptableCanonicalForm("etc.") shouldBe false
  }

  test("Rejects a form with a digit") {
    LexiconFilters.isAcceptableCanonicalForm("km2") shouldBe false
  }

  test("Rejects a proper noun or acronym, any uppercase letter") {
    LexiconFilters.isAcceptableCanonicalForm("Brasil") shouldBe false
    LexiconFilters.isAcceptableCanonicalForm("ONU") shouldBe false
  }

  test("Rejects an accented uppercase letter too") {
    LexiconFilters.isAcceptableCanonicalForm("Étnico") shouldBe false
  }

  test("Digit boundary: '0' and '9' are rejected, their neighbors '/' and ':' are accepted") {
    LexiconFilters.isAcceptableCanonicalForm("a0a") shouldBe false
    LexiconFilters.isAcceptableCanonicalForm("a9a") shouldBe false
    LexiconFilters.isAcceptableCanonicalForm("a/a") shouldBe true
    LexiconFilters.isAcceptableCanonicalForm("a:a") shouldBe true
  }

  test("A normalized form must be within 3 and 16 letters") {
    LexiconFilters.isAcceptableNormalizedForm("AB") shouldBe false
    LexiconFilters.isAcceptableNormalizedForm("ABC") shouldBe true
    LexiconFilters.isAcceptableNormalizedForm("A".repeat(16)) shouldBe true
    LexiconFilters.isAcceptableNormalizedForm("A".repeat(17)) shouldBe false
  }

  test("A normalized form must be pure A-Z, catching what normalization could not reduce") {
    LexiconFilters.isAcceptableNormalizedForm("CASA") shouldBe true
    LexiconFilters.isAcceptableNormalizedForm("CAS A") shouldBe false
    LexiconFilters.isAcceptableNormalizedForm("CM²") shouldBe false
  }

  test("Letter boundary: '@' and '[' just outside A-Z are rejected") {
    LexiconFilters.isAcceptableNormalizedForm("CA@A") shouldBe false
    LexiconFilters.isAcceptableNormalizedForm("CA[A") shouldBe false
  }

  test("a form on the exclusion list is refused, and the word it is a corruption of is not") {
    LexiconFilters.isAcceptableNormalizedForm("MENAS") shouldBe false
    LexiconFilters.isAcceptableNormalizedForm("MENOS") shouldBe true
  }
})
