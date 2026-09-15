// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

private val Whitespace = Regex("\\s+")
private const val ZeroPlaceholder = "0"

/**
 * Parses a Hunspell `.aff` file (VERO's `pt_BR.aff`: `FLAG UTF-8`, 103 affix classes, no
 * COMPOUND/NEEDAFFIX/CIRCUMFIX/AF/AM directives, dossier §11 phase 1). Every other directive
 * (`SET`, `TRY`, `MAP`, `BREAK`, `REP`...) is intentionally ignored: none of them affect which forms
 * a spellchecked word can take, only suggestion quality, which this game does not use.
 */
object AffixFileParser {
  fun parse(lines: List<String>): AffixFile {
    var forbiddenWordFlag: Char? = null
    var noSuggestFlag: Char? = null
    val classes = LinkedHashMap<Char, AffixClass>()

    var index = 0
    while (index < lines.size) {
      val line = lines[index].trim()
      when {
        line.startsWith("FORBIDDENWORD") -> forbiddenWordFlag = singleFlagArgument(line)
        line.startsWith("NOSUGGEST") -> noSuggestFlag = singleFlagArgument(line)
        line.startsWith("PFX ") || line.startsWith("SFX ") -> {
          val header = line.split(Whitespace)
          val ruleCount = header[3].toInt()
          val affixClass = AffixClass(
            flag = header[1].single(),
            kind = if (header[0] == "PFX") AffixKind.Prefix else AffixKind.Suffix,
            crossProduct = header[2] == "Y",
            rules = (1..ruleCount).map { offset -> parseRule(lines[index + offset]) },
          )
          classes[affixClass.flag] = affixClass
          index += ruleCount
        }
      }
      index++
    }

    return AffixFile(classes, forbiddenWordFlag, noSuggestFlag)
  }

  private fun singleFlagArgument(directiveLine: String): Char = directiveLine.split(Whitespace)[1].single()

  /** A rule line: `PFX/SFX flag strip add[/contflags] condition [morph...]`. Morph fields are unused. */
  private fun parseRule(line: String): AffixRule {
    val fields = line.trim().split(Whitespace)
    val strip = zeroAware(fields[2])
    val addField = fields[3]
    val slash = addField.indexOf('/')
    val add: String
    val continuationFlags: Set<Char>
    if (slash == -1) {
      add = zeroAware(addField)
      continuationFlags = emptySet()
    } else {
      add = zeroAware(addField.substring(0, slash))
      continuationFlags = addField.substring(slash + 1).toSet()
    }
    val condition = AffixCondition.parse(fields[4])
    return AffixRule(strip, add, condition, continuationFlags)
  }

  private fun zeroAware(field: String): String = if (field == ZeroPlaceholder) "" else field
}
