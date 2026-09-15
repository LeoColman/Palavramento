// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import br.com.colman.palavramento.domain.lexicon.LexiconEntry
import br.com.colman.palavramento.domain.lexicon.TrieLexicon
import br.com.colman.palavramento.domain.lexicon.lookup
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.random.Random
import kotlin.system.measureNanoTime

private const val SampleSize = 1000
private const val RandomProbeSeed = 20260914L
private const val MaxLoadMillis = 1000L
private const val NanosPerMilli = 1_000_000L
private const val MinProbeLength = 3
private const val MaxProbeLength = 16
private const val AlphabetSize = 26

private fun formsTsv(): File {
  val path = System.getProperty("palavramento.lexicon.formsTsv")
  checkNotNull(path) { "palavramento.lexicon.formsTsv system property not set; run tests through Gradle" }
  return File(path)
}

private fun readAllRows(): List<FormRow> = formsTsv().bufferedReader().useLines { lines ->
  lines.map { line ->
    val (canonical, normalized, rank) = line.split('\t')
    FormRow(canonical, normalized, rank.toInt())
  }.toList()
}

/**
 * Acceptance tests for the phase 1 lexicon pipeline (dossier §11 phase 1): the artifact loads fast
 * and its contents agree with an independent recomputation of the collapsing rule over the whole
 * `forms.tsv`, both for real forms and for strings that are not in the lexicon at all.
 *
 * `forms.tsv` has 2M+ lines: reading and collapsing it is done at most once per test run (kotest's
 * default `SingleInstance` isolation keeps one spec instance for every `test` block below), not once
 * per assertion, so the suite stays fast without weakening any single test's coverage.
 */
class LexiconLoaderTest : FunSpec({
  val rows by lazy { readAllRows() }
  val expected by lazy { CanonicalForms.collapse(rows) }

  test("The artifact loads from the classpath in under a second") {
    // Measures LexiconLoader.load() alone, deliberately not sharing the lazy lexicon below with the
    // other tests, so this timing is never inflated by, or hidden behind, their own setup cost.
    var lexicon: TrieLexicon? = null
    val elapsedNanos = measureNanoTime { lexicon = LexiconLoader.load() }
    lexicon.shouldNotBeNull()
    (elapsedNanos / NanosPerMilli) shouldBeLessThan MaxLoadMillis
  }

  test("A random sample of forms.tsv lines is found with the collapsing rule's display and rank") {
    val lexicon = LexiconLoader.load()
    val random = Random(RandomProbeSeed)
    val sampleIndices = generateSequence { random.nextInt(rows.size) }.distinct().take(SampleSize).toList()
    sampleIndices.forEach { index ->
      val row = rows[index]
      lexicon.lookup(row.normalized) shouldBe expected.getValue(row.normalized)
    }
  }

  test("Random A-Z strings absent from forms.tsv are not found") {
    val lexicon = LexiconLoader.load()
    val random = Random(RandomProbeSeed)
    val probes = generateSequence {
      val length = random.nextInt(MinProbeLength, MaxProbeLength + 1)
      (1..length).joinToString("") { ('A' + random.nextInt(AlphabetSize)).toString() }
    }.filter { it !in expected.keys }.distinct().take(SampleSize).toList()

    probes.forEach { probe -> lexicon.lookup(probe).shouldBeNull() }
  }

  test("pais and país collapse to one entry, displayed as the more frequent spelling") {
    val lexicon = LexiconLoader.load()
    val collapsed = lexicon.lookup("PAIS")
    collapsed.shouldNotBeNull()
    collapsed shouldBe expected.getValue("PAIS")
    collapsed.display shouldBe "pais"
  }

  test("The collapsing rule itself picked pais over país because pais ranks better") {
    // Pins the real-data numbers ADR 0004 reports, so a dictionary/frequency-list update that flips
    // this is a visible, deliberate change rather than a silent one.
    expected.getValue("PAIS") shouldBe LexiconEntry("pais", 467)
  }
})
