// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.scoring

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Base point value of every normalized letter (A-Z), dossier 1.3, before a [Mutator.ValuableLetter]
 * override.
 *
 * Loaded from a versioned JSON resource instead of being a constant table in code, so phase 1 can
 * paste in values calibrated against the real lexicon's letter frequency without touching Kotlin.
 * The constructor also accepts a plain map directly, so tests do not need a resource file on the
 * classpath to build a small fixture table.
 *
 * The dossier's starting table lists O at both value 1 and 2, and C/T/L at both value 3 and 4
 * (marked with an asterisk as still undecided). This resource resolves every such ambiguity by
 * keeping the smaller of the two listed values, which also tracks pt-BR letter frequency: O, at
 * roughly 10.7% of letters, sits with the value-1 vowels (A, E, S) rather than the value-2 cluster;
 * T (4.3%) and C (3.9%) sit closer to M's 4.7% (value 3) than to B/G's ~1% (value 4). L keeps its
 * only listed value, 3, even though it carries the same asterisk.
 */
class LetterValueTable(val version: Int, private val values: Map<Char, Int>) {
  init {
    val missing = ('A'..'Z').filterNot { it in values }
    require(missing.isEmpty()) { "Letter value table is missing letter(s): $missing" }
  }

  /** Base point value for [letter], independent of any mutator. */
  fun value(letter: Char): Int = values.getValue(letter.uppercaseChar())

  companion object {
    private const val ResourcePath = "/letter-values.json"

    /** Parses a table from JSON shaped `{"version": Int, "values": {"A": Int, ...}}`. */
    fun fromJson(json: String): LetterValueTable {
      val resource = Json.decodeFromString<LetterValuesResource>(json)
      return LetterValueTable(resource.version, resource.values.mapKeys { (key, _) -> key.single() })
    }

    /** The table shipped as a build resource, the one production code should use. */
    val default: LetterValueTable by lazy {
      val stream = requireNotNull(LetterValueTable::class.java.getResourceAsStream(ResourcePath)) {
        "$ResourcePath missing from the classpath"
      }
      fromJson(stream.bufferedReader().use { it.readText() })
    }
  }
}

@Serializable
private data class LetterValuesResource(val version: Int, val values: Map<String, Int>)
