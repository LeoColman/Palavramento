// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.solver

/**
 * One word the [Solver] found on a board: its normalized (A-Z) form used for deduplication, the
 * lexicon's display form, the best-scoring path that spells it, that path's score and its tier.
 *
 * [path] is a plain tile-index list, the same shape the wire protocol uses, rather than the
 * domain's [br.com.colman.palavramento.domain.board.Path] wrapper: nothing here needs to
 * re-validate an already-solved path against a board.
 */
data class SolvedWord(
  val normalized: String,
  val display: String,
  val path: List<Int>,
  val score: Int,
  val tier: WordTier,
)
