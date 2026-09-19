// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.generator

/**
 * Acceptance thresholds for how well a board's special tiles (digraph, valuable-letter, alternatives,
 * corner) integrate into its own solution (owner request 2026-09-18: "vamos tornar esses jogos um
 * pouco mais divertidos"). The owner's complaint was concrete: a `DIGRAFOS` round could place a
 * digraph tile in a corner with no vowel around it, so no solved word ever touched it, and the
 * mutator was pointless to the player. [BoardGenerator] checks every special tile a mutator placed
 * against these thresholds, on top of [GenerationCriteria], and redraws the board when they are not
 * met.
 *
 * A tile is "used by a word" when that word's solved path passes through the tile's index, exactly
 * like [br.com.colman.palavramento.domain.solver.SolvedWord.path].
 *
 * Every field is a parameter, not a constant, for the same reason as [GenerationCriteria]'s own
 * fields: a test with a small synthetic lexicon needs to inject thresholds that lexicon can actually
 * reach. [BoardGenerator.generate]'s relaxation loop shrinks these together with the rest of
 * [GenerationCriteria] ([relaxed]), so generation is still guaranteed to terminate.
 */
data class SpecialTileCriteria(
  /**
   * Minimum solved words that must use a digraph tile, a valuable-letter tile, or the alternatives
   * tile overall (both its options combined). Default 5.
   */
  val minWordsPerTile: Int = DefaultMinWordsPerTile,
  /** Minimum solved words that must use each corner tile of a `LETRA_NOS_CANTOS` round. Default 2. */
  val minWordsPerCorner: Int = DefaultMinWordsPerCorner,
  /**
   * Minimum solved words that must use *each* of the alternatives tile's two options (dossier: an
   * "A" word and an "F" word both need to be reachable, not just one of them). Default 2.
   */
  val minWordsPerOption: Int = DefaultMinWordsPerOption,
) {
  /** Shrinks every threshold by [factor] (same technique as [GenerationCriteria.relaxed]). */
  fun relaxed(factor: Double): SpecialTileCriteria = copy(
    minWordsPerTile = shrink(minWordsPerTile, factor),
    minWordsPerCorner = shrink(minWordsPerCorner, factor),
    minWordsPerOption = shrink(minWordsPerOption, factor),
  )

  private fun shrink(value: Int, factor: Double): Int = (value * (1 - factor)).toInt().coerceAtLeast(0)

  companion object {
    const val DefaultMinWordsPerTile = 5
    const val DefaultMinWordsPerCorner = 2
    const val DefaultMinWordsPerOption = 2
  }
}
