// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.lexicon

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.stringPattern
import io.kotest.property.checkAll

// Fixtures are built inside each test, never in the spec body: an exception thrown while the spec
// is constructed is invisible to PIT's Kotest plugin, so mutants that break construction survive.
private fun sample() = InMemoryLexicon.of("limo", "loâ", "lima")

class InMemoryLexiconTest : FunSpec({
  test("Finds words by their normalized form and keeps the display form") {
    val loa = sample().lookup("LOA")!!
    loa.display shouldBe "loâ"
    loa.frequencyRank shouldBe 2
    sample().lookup("LIMO") shouldBe LexiconEntry("limo", 1)
  }

  test("A prefix of a word is a node but not an entry") {
    sample().walk("LIM") shouldNotBe Lexicon.NoNode
    sample().lookup("LIM") shouldBe null
  }

  test("A path that leaves the lexicon stays out") {
    sample().walk("LX") shouldBe Lexicon.NoNode
    sample().walk("LXA") shouldBe Lexicon.NoNode
    sample().lookup("ZZZ") shouldBe null
  }

  test("Walking continues from an intermediate node") {
    val lexicon = sample()
    lexicon.entry(lexicon.walk("MO", from = lexicon.walk("LI"))) shouldBe LexiconEntry("limo", 1)
  }

  test("The empty prefix is the root, which is not a word") {
    sample().walk("") shouldBe Lexicon.Root
    sample().entry(Lexicon.Root) shouldBe null
  }

  test("Rejects keys that are not normalized") {
    listOf("loâ", "", "lima", "LIM4", "@", "[").forEach { key ->
      shouldThrow<IllegalArgumentException> { InMemoryLexicon(mapOf(key to LexiconEntry(key, 1))) }
    }
  }

  test("Accepts the first and last letters of the alphabet") {
    InMemoryLexicon(mapOf("AZ" to LexiconEntry("az", 1))).lookup("AZ") shouldBe LexiconEntry("az", 1)
  }

  test("Every inserted word is found, and nothing else") {
    checkAll(Arb.list(Arb.stringPattern("[A-E]{1,6}"), 0..30), Arb.string(1..6)) { words, probe ->
      val subject = InMemoryLexicon(words.associateWith { LexiconEntry(it, 1) })
      words.forEach { subject.lookup(it) shouldBe LexiconEntry(it, 1) }
      if (probe !in words) subject.lookup(probe) shouldBe null
    }
  }
})
