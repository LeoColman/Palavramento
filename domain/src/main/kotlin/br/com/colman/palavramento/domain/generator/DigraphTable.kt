// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.generator

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * pt-BR digraph candidates drawn by [Mutator.Digraphs] (ADR 0012): `QU`, `NH`, `LH`, `CH`, `RR`,
 * `SS`, `GU`. `ÃO` is deliberately excluded: a board tile only ever holds normalized A-Z letters
 * (dossier 1.6, [br.com.colman.palavramento.domain.board.Tile]), so a digraph tile carrying `ÃO`
 * would render as the two plain letters `AO`. That both loses the nasal diacritic the player would
 * expect to see and, worse, lets the tile silently match unrelated words spelled with a literal `AO`
 * (e.g. "caos"), which would mislead the player about what the tile actually represents. Every other
 * candidate here is already diacritic-free in its normalized form, so none of them has that problem.
 *
 * Weights are a v1 starting point (like `letter-weights.json` version 1 before
 * `docs/calibracao-letras.md` calibrated it against the real lexicon), not a corpus measurement:
 * `QU`/`CH`/`SS` rank higher as intuitively the most common pt-BR digraphs, `LH`/`GU` lower. A future
 * phase can recalibrate this the same way the letter tables were.
 *
 * Loaded from a versioned JSON resource, a sibling of `letter-weights.json`/`letter-values.json`, for
 * the same reason: the table can be recalibrated without touching Kotlin.
 */
class DigraphTable(val version: Int, entries: List<DigraphEntry>) {
  init {
    require(entries.isNotEmpty()) { "Digraph table must have at least one entry" }
    require(entries.all { it.weight > 0.0 }) { "Digraph weights must be positive: $entries" }
    require(entries.all { it.letters.length == 2 && it.letters.all { letter -> letter in 'A'..'Z' } }) {
      "Digraph letters must be exactly two normalized A-Z letters: $entries"
    }
  }

  private val letters: List<String> = entries.map { it.letters }

  // Cumulative sums turn a weighted draw into "pick a point on [0, total) and find its bucket",
  // the same technique as LetterWeightTable.
  private val cumulativeWeights: DoubleArray = run {
    var running = 0.0
    DoubleArray(entries.size) { index ->
      running += entries[index].weight
      running
    }
  }

  private val total: Double = cumulativeWeights.last()

  /** Draws one digraph's letters (e.g. `"QU"`), weighted by its entry. */
  fun sample(random: Random): String {
    val target = random.nextDouble() * total
    val index = cumulativeWeights.indexOfFirst { it > target }
    return letters[if (index == -1) letters.lastIndex else index]
  }

  companion object {
    private const val ResourcePath = "/digraphs.json"

    /** Parses a table from JSON shaped `{"version": Int, "entries": [{"letters": String, "weight": Double}, ...]}`. */
    fun fromJson(json: String): DigraphTable {
      val resource = Json.decodeFromString<DigraphsResource>(json)
      return DigraphTable(resource.version, resource.entries)
    }

    /** The table shipped as a build resource, the one production code should use. */
    val default: DigraphTable by lazy {
      val stream = requireNotNull(DigraphTable::class.java.getResourceAsStream(ResourcePath)) {
        "$ResourcePath missing from the classpath"
      }
      fromJson(stream.bufferedReader().use { it.readText() })
    }
  }
}

/** One digraph candidate: its two normalized letters and its draw weight. */
@Serializable
data class DigraphEntry(val letters: String, val weight: Double)

@Serializable
private data class DigraphsResource(val version: Int, val entries: List<DigraphEntry>)
