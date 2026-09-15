// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

/** Whether an affix class strips/adds at the start ([Prefix]) or the end ([Suffix]) of a stem. */
enum class AffixKind { Prefix, Suffix }

/**
 * One rule inside a PFX/SFX block: `strip` characters are removed and `add` characters appended
 * (at the start for a prefix, at the end for a suffix) when [condition] matches the stem, per the
 * Hunspell `.aff` format. `0` in the source file means an empty strip or add, already normalized to
 * `""` by the parser. [continuationFlags] are the flags the *derived* word carries (dossier §11
 * phase 1: applied one extra level deep, never recursively).
 */
data class AffixRule(
  val strip: String,
  val add: String,
  val condition: AffixCondition,
  val continuationFlags: Set<Char> = emptySet(),
)

/** One `PFX`/`SFX` block: all rules that fire on the same single-character flag. */
data class AffixClass(
  val flag: Char,
  val kind: AffixKind,
  val crossProduct: Boolean,
  val rules: List<AffixRule>,
)

/**
 * Parsed `.aff` file: affix classes keyed by flag, plus the two special flags this lexicon cares
 * about (dossier §11 phase 1). Both are nullable because a `.aff` file need not declare them.
 */
data class AffixFile(
  val classes: Map<Char, AffixClass>,
  val forbiddenWordFlag: Char?,
  val noSuggestFlag: Char?,
)

/** One line of a `.dic` file: a stem and the affix/special flags attached to it. */
data class DicEntry(val stem: String, val flags: Set<Char>)
