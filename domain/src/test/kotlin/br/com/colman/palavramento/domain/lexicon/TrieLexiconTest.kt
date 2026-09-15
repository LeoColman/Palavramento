// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.lexicon

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.stringPattern
import io.kotest.property.checkAll
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

// Fixtures are built inside each test, never in the spec body: an exception thrown while the spec is
// constructed is invisible to PIT's Kotest plugin, so mutants that break construction survive.
private fun sample() = TrieLexicon.build(
  mapOf(
    "LOA" to LexiconEntry("loâ", 2),
    "LIMO" to LexiconEntry("limo", 1),
    "LIMA" to LexiconEntry("lima", 3),
  ),
)

private fun roundTrip(lexicon: TrieLexicon): TrieLexicon {
  val bytes = ByteArrayOutputStream()
  lexicon.write(bytes)
  return TrieLexicon.read(ByteArrayInputStream(bytes.toByteArray()))
}

class TrieLexiconTest : FunSpec({
  test("Finds words by their normalized form and keeps the display form") {
    val loa = sample().lookup("LOA")!!
    loa.display shouldBe "loâ"
    loa.frequencyRank shouldBe 2
    sample().lookup("LIMO") shouldBe LexiconEntry("limo", 1)
  }

  test("A word whose display is the lowercase of its normalized form needs no override") {
    val entry = sample().lookup("LIMO")!!
    entry.display shouldBe "limo"
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

  test("The empty prefix is the root, which is not a word") {
    sample().walk("") shouldBe Lexicon.Root
    sample().entry(Lexicon.Root) shouldBe null
    Lexicon.Root shouldBe 0
  }

  test("An empty lexicon has only the root, which resolves no letter") {
    val empty = TrieLexicon.build(emptyMap())
    empty.child(Lexicon.Root, 'A') shouldBe Lexicon.NoNode
    empty.nodeCount shouldBe 1
  }

  test("Rejects keys that are not normalized") {
    listOf("loâ", "", "lima", "LIM4", "@", "[").forEach { key ->
      shouldThrow<IllegalArgumentException> { TrieLexicon.build(mapOf(key to LexiconEntry(key, 1))) }
    }
  }

  test("Accepts the first and last letters of the alphabet") {
    TrieLexicon.build(mapOf("AZ" to LexiconEntry("az", 1))).lookup("AZ") shouldBe LexiconEntry("az", 1)
  }

  test("Every inserted word is found, and nothing else") {
    checkAll(Arb.list(Arb.stringPattern("[A-E]{1,6}"), 0..30), Arb.stringPattern("[A-E]{1,6}")) { words, probe ->
      val subject = TrieLexicon.build(words.associateWith { LexiconEntry(it, 1) })
      words.forEach { subject.lookup(it) shouldBe LexiconEntry(it, 1) }
      if (probe !in words) subject.lookup(probe) shouldBe null
    }
  }

  test("Write then read agrees with the lexicon it was built from, matching InMemoryLexicon") {
    checkAll(
      Arb.map(Arb.stringPattern("[A-E]{1,6}"), Arb.int(1..500), minSize = 0, maxSize = 30),
    ) { wordsToRanks ->
      val entries = wordsToRanks.mapValues { (word, r) -> LexiconEntry(word.lowercase() + "!", r) }
      val reference = InMemoryLexicon(entries)
      val restored = roundTrip(TrieLexicon.build(entries))

      entries.keys.forEach { normalized ->
        restored.lookup(normalized) shouldBe reference.lookup(normalized)
      }
      restored.lookup("ZZZZZZ") shouldBe reference.lookup("ZZZZZZ")
    }
  }

  test("Round trip preserves an override display distinct from the lowercase spelling") {
    val restored = roundTrip(sample())
    val loa = restored.entry(restored.walk("LOA"))!!
    loa.display shouldBe "loâ"
    loa.frequencyRank shouldBe 2
  }

  test("Round trip preserves a default display equal to the lowercase spelling") {
    val restored = roundTrip(sample())
    val limo = restored.entry(restored.walk("LIMO"))!!
    limo.display shouldBe "limo"
    limo.frequencyRank shouldBe 1
  }

  test("An unranked entry keeps the sentinel rank across a round trip") {
    val lexicon = TrieLexicon.build(mapOf("XYZ" to LexiconEntry("xyz", LexiconEntry.Unranked)))
    val restored = roundTrip(lexicon)
    restored.lookup("XYZ") shouldBe LexiconEntry("xyz", LexiconEntry.Unranked)
  }

  test("Reading a stream without the magic header fails") {
    shouldThrow<IllegalArgumentException> { TrieLexicon.read(ByteArrayInputStream(ByteArray(20))) }
  }

  test("Reading a stream with an unsupported version fails") {
    val bytes = ByteArrayOutputStream()
    sample().write(bytes)
    val corrupted = bytes.toByteArray().copyOf()
    // Bytes 4..7 (big-endian Int right after the 4-byte magic) hold the format version.
    corrupted[7] = corrupted[7].plus(1).toByte()
    shouldThrow<IllegalArgumentException> { TrieLexicon.read(ByteArrayInputStream(corrupted)) }
  }

  test("A display matching the lowercase spelling costs no override bytes, an override does") {
    val noOverride = TrieLexicon.build(mapOf("LIMO" to LexiconEntry("limo", 1)))
    val withOverride = TrieLexicon.build(mapOf("LIMO" to LexiconEntry("LIMO-X", 1)))
    val baseline = ByteArrayOutputStream().apply { noOverride.write(this) }.size()
    val overridden = ByteArrayOutputStream().apply { withOverride.write(this) }.size()
    // One extra Int for the extra override offset, plus the override string's own UTF-8 bytes.
    overridden shouldBe baseline + Int.SIZE_BYTES + "LIMO-X".toByteArray(StandardCharsets.UTF_8).size
  }

  test("Writing flushes the underlying stream") {
    var flushed = false
    val tracking = object : OutputStream() {
      override fun write(b: Int) = Unit
      override fun flush() {
        flushed = true
      }
    }
    sample().write(tracking)
    flushed shouldBe true
  }

  test("Walking continues from an intermediate node") {
    val lexicon = sample()
    lexicon.entry(lexicon.walk("MO", from = lexicon.walk("LI"))) shouldBe LexiconEntry("limo", 1)
  }
})
