// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

/**
 * Expands a Hunspell dictionary into every surface form it accepts, without `unmunch` (dossier §11
 * phase 1, ADR 0004). For each `.dic` entry this applies:
 *
 * - every suffix rule whose flag is on the stem and whose condition matches the stem's end;
 * - every prefix rule whose flag is on the stem and whose condition matches the stem's start;
 * - every prefix+suffix cross product, when both classes are cross-product (`Y`): the prefix's
 *   condition is checked against the already-suffixed word, matching how Hunspell itself validates
 *   combined forms (it strips the suffix first, then the prefix);
 * - one extra level of affixation for a derived form's continuation flags. A continuation flag
 *   replaces the derived word's flag set (it does not inherit the stem's flags), matching Hunspell
 *   semantics. Only one extra level is applied, so recursion never happens and the algorithm always
 *   terminates regardless of the input `.aff`.
 *
 * A generated form (base or derived) is dropped when its own effective flag set carries
 * [AffixFile.forbiddenWordFlag], and also when it carries [AffixFile.noSuggestFlag]: the dossier asks
 * to filter abbreviations and proper nouns explicitly (§2.2), and NOSUGGEST words in VERO are mostly
 * regionalisms or vulgar terms not worth scoring in a word game (documented in ADR 0004). Because
 * flags do not inherit through affixation, only the specific flagged occurrence is dropped: a vulgar
 * stem's regular conjugations are not automatically dropped unless the source dictionary flags them
 * too. That is a property of the source data, not a shortcut of this expander.
 */
object HunspellExpander {
  fun expand(dictionary: List<DicEntry>, affixes: AffixFile): Sequence<String> = sequence {
    for (entry in dictionary) {
      yieldAll(expandEntry(entry.stem, entry.flags, affixes))
    }
  }

  private fun expandEntry(stem: String, flags: Set<Char>, affixes: AffixFile): List<String> {
    val accepted = mutableListOf<String>()
    fun emit(word: String, ownFlags: Set<Char>) {
      if (affixes.forbiddenWordFlag != null && affixes.forbiddenWordFlag in ownFlags) return
      if (affixes.noSuggestFlag != null && affixes.noSuggestFlag in ownFlags) return
      accepted += word
    }

    emit(stem, flags)

    val suffixed = applyClasses(stem, flags, affixes, AffixKind.Suffix, ::emit)
    applyClasses(stem, flags, affixes, AffixKind.Prefix, ::emit)

    for ((suffixedWord, suffixContinuationFlags) in suffixed) {
      for (flag in flags) {
        val prefixClass = affixes.classes[flag] ?: continue
        if (prefixClass.kind != AffixKind.Prefix || !prefixClass.crossProduct) continue
        for (rule in prefixClass.rules) {
          if (!applicable(rule, suffixedWord, AffixKind.Prefix)) continue
          val derived = derive(rule, suffixedWord, AffixKind.Prefix)
          val combinedFlags = suffixContinuationFlags + rule.continuationFlags
          emit(derived, combinedFlags)
          applyOneLevel(derived, combinedFlags, affixes, ::emit)
        }
      }
    }

    return accepted
  }

  /** Applies every rule of [kind] whose flag is in [flags], emitting derived forms and one extra level. */
  private fun applyClasses(
    stem: String,
    flags: Set<Char>,
    affixes: AffixFile,
    kind: AffixKind,
    emit: (String, Set<Char>) -> Unit,
  ): List<Pair<String, Set<Char>>> {
    // Only cross-product-eligible classes are returned: they are the only ones a caller ever chains.
    val crossProductDerived = mutableListOf<Pair<String, Set<Char>>>()
    for (flag in flags) {
      val affixClass = affixes.classes[flag] ?: continue
      if (affixClass.kind != kind) continue
      for (rule in affixClass.rules) {
        if (!applicable(rule, stem, kind)) continue
        val derived = derive(rule, stem, kind)
        emit(derived, rule.continuationFlags)
        applyOneLevel(derived, rule.continuationFlags, affixes, emit)
        if (affixClass.crossProduct) crossProductDerived += derived to rule.continuationFlags
      }
    }
    return crossProductDerived
  }

  /** The one extra level of affixation dossier §11 phase 1 asks for: no further recursion after this. */
  private fun applyOneLevel(word: String, flags: Set<Char>, affixes: AffixFile, emit: (String, Set<Char>) -> Unit) {
    for (flag in flags) {
      val affixClass = affixes.classes[flag] ?: continue
      for (rule in affixClass.rules) {
        if (!applicable(rule, word, affixClass.kind)) continue
        emit(derive(rule, word, affixClass.kind), rule.continuationFlags)
      }
    }
  }

  private fun applicable(rule: AffixRule, word: String, kind: AffixKind): Boolean {
    if (word.length < rule.strip.length) return false
    return if (kind == AffixKind.Suffix) rule.condition.matchesEnd(word) else rule.condition.matchesStart(word)
  }

  private fun derive(rule: AffixRule, word: String, kind: AffixKind): String = if (kind == AffixKind.Suffix) {
    word.substring(0, word.length - rule.strip.length) + rule.add
  } else {
    rule.add + word.substring(rule.strip.length)
  }
}
