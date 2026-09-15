// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.generator

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.solver.SolvedWord

/**
 * A board [BoardGenerator] accepted, together with its full solution and the bookkeeping needed to
 * reproduce or audit the generation run.
 *
 * [criteriaUsed] is the criteria that were actually satisfied, which may be a [GenerationCriteria]
 * relaxed one or more times from the caller's original request (dossier 3, step 5).
 */
data class GenerationResult(
  val board: Board,
  val solution: List<SolvedWord>,
  val seed: Long,
  val criteriaUsed: GenerationCriteria,
  val attempts: Int,
)
