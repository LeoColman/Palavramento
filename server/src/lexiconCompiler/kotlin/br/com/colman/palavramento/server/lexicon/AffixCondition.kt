// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

/**
 * One unit of a Hunspell affix condition: either any character (`.`), a literal character, or a
 * bracket class (`[abc]`, `[^abc]`).
 */
private sealed interface ConditionUnit {
  fun matches(char: Char): Boolean

  object Any : ConditionUnit {
    override fun matches(char: Char) = true
  }

  data class Literal(val char: Char) : ConditionUnit {
    override fun matches(char: Char) = char == this.char
  }

  data class CharClass(val chars: Set<Char>, val negate: Boolean) : ConditionUnit {
    override fun matches(char: Char) = (char in chars) != negate
  }
}

/**
 * A Hunspell affix condition (`.aff` 4th column): a short sequence of [ConditionUnit], matched
 * against the whole stem, anchored at the start for prefixes and at the end for suffixes. This is
 * independent of how many characters the rule actually strips (dossier §11 phase 1): a suffix can
 * require two characters of context while stripping only one, as in `SFX D o a [^ã]o`.
 */
class AffixCondition private constructor(private val units: List<ConditionUnit>) {

  /** Number of characters this condition looks at. */
  val length: Int get() = units.size

  fun matchesEnd(word: CharSequence): Boolean {
    if (word.length < units.size) return false
    val offset = word.length - units.size
    return units.indices.all { units[it].matches(word[offset + it]) }
  }

  fun matchesStart(word: CharSequence): Boolean {
    if (word.length < units.size) return false
    return units.indices.all { units[it].matches(word[it]) }
  }

  companion object {
    /** A condition matching everything, for the empty (all-dots) case. Kept for clarity in tests. */
    val Always = AffixCondition(emptyList())

    fun parse(pattern: String): AffixCondition {
      val units = mutableListOf<ConditionUnit>()
      var index = 0
      while (index < pattern.length) {
        val char = pattern[index]
        when (char) {
          '.' -> {
            units += ConditionUnit.Any
            index++
          }

          '[' -> {
            val close = pattern.indexOf(']', index)
            require(close != -1) { "Unterminated character class in condition '$pattern'" }
            var body = pattern.substring(index + 1, close)
            val negate = body.startsWith("^")
            if (negate) body = body.substring(1)
            require(body.isNotEmpty()) { "Empty character class in condition '$pattern'" }
            units += ConditionUnit.CharClass(body.toSet(), negate)
            index = close + 1
          }

          else -> {
            units += ConditionUnit.Literal(char)
            index++
          }
        }
      }
      return AffixCondition(units)
    }
  }
}
