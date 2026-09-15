// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import br.com.colman.palavramento.domain.WordNormalizer

/**
 * One surviving canonical form after expansion and filtering (dossier §2.2): its own spelling, its
 * normalized (A-Z, tile) spelling, and its own frequency rank, `null` when it is absent from
 * [FrequencyList] ("especialista", dossier §1.7). This is one row of `forms.tsv`; collapsing several
 * rows that share a [normalized] form into one scoring [br.com.colman.palavramento.domain.lexicon.LexiconEntry]
 * is [CanonicalForms]' job, not this one.
 */
data class FormRow(val canonical: String, val normalized: String, val rank: Int?)

/**
 * Runs the whole build-time lexicon pipeline: expand the Hunspell dictionary, then keep only the
 * forms that pass every filter in [LexiconFilters], deduplicated by canonical spelling (the same
 * surface form can be reachable through more than one affix rule) and looked up in [frequency].
 */
object LexiconPipeline {
  fun buildRows(dictionary: List<DicEntry>, affixes: AffixFile, frequency: FrequencyList): List<FormRow> {
    val seenCanonicalForms = HashSet<String>()
    val rows = mutableListOf<FormRow>()
    for (canonical in HunspellExpander.expand(dictionary, affixes)) {
      if (!LexiconFilters.isAcceptableCanonicalForm(canonical)) continue
      val normalized = WordNormalizer.normalize(canonical)
      if (!LexiconFilters.isAcceptableNormalizedForm(normalized)) continue
      if (!seenCanonicalForms.add(canonical)) continue
      rows += FormRow(canonical, normalized, frequency.rankOf(canonical.lowercase()))
    }
    return rows
  }
}
