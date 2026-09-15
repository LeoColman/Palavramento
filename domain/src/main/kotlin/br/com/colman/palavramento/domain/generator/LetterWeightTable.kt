// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.generator

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * pt-BR letter frequency weights used to draw board letters (dossier 3.1), so a generated grid has
 * a realistic vowel/consonant mix instead of a uniform draw that could starve a grid of vowels.
 *
 * Loaded from a versioned JSON resource, a sibling of `letter-values.json`, for the same reason:
 * phase 1 can paste in weights recalibrated from the real corpus without touching Kotlin.
 */
class LetterWeightTable(val version: Int, weights: Map<Char, Double>) {
  init {
    val missing = ('A'..'Z').filterNot { it in weights }
    require(missing.isEmpty()) { "Letter weight table is missing letter(s): $missing" }
    require(weights.values.all { it > 0.0 }) { "Letter weights must be positive: $weights" }
  }

  private val letters: List<Char> = weights.keys.toList()

  // Cumulative sums turn a weighted draw into "pick a point on [0, total) and find its bucket".
  private val cumulativeWeights: DoubleArray = run {
    var running = 0.0
    DoubleArray(letters.size) { index ->
      running += weights.getValue(letters[index])
      running
    }
  }

  private val total: Double = cumulativeWeights.last()

  /** Draws one letter, weighted by its pt-BR frequency. */
  fun sample(random: Random): Char {
    val target = random.nextDouble() * total
    val index = cumulativeWeights.indexOfFirst { it > target }
    return letters[if (index == -1) letters.lastIndex else index]
  }

  companion object {
    private const val ResourcePath = "/letter-weights.json"

    /** Parses a table from JSON shaped `{"version": Int, "weights": {"A": Double, ...}}`. */
    fun fromJson(json: String): LetterWeightTable {
      val resource = Json.decodeFromString<LetterWeightsResource>(json)
      return LetterWeightTable(resource.version, resource.weights.mapKeys { (key, _) -> key.single() })
    }

    /** The table shipped as a build resource, the one production code should use. */
    val default: LetterWeightTable by lazy {
      val stream = requireNotNull(LetterWeightTable::class.java.getResourceAsStream(ResourcePath)) {
        "$ResourcePath missing from the classpath"
      }
      fromJson(stream.bufferedReader().use { it.readText() })
    }
  }
}

@Serializable
private data class LetterWeightsResource(val version: Int, val weights: Map<String, Double>)
