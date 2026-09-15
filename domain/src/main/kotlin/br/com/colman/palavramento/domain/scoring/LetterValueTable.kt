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
 * Version 2 of the resource is calibrated against the real lexicon (docs/calibracao-letras.md):
 * letters are banded by how many playable forms use them, on the dossier's own 1..10 scale, which
 * keeps typical boards inside the generator's 2500..6000 score window.
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
