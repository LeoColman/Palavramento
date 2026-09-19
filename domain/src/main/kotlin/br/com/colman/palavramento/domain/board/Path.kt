// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.board

/**
 * A traced sequence of tile indices, in the order the player dragged over them.
 *
 * Wire messages carry the same shape as a plain `List<Int>` (dossier 5.2), so [Path] stays a thin
 * domain wrapper used only while validating or scoring a submission, not a serialized DTO itself.
 */
data class Path(val indices: List<Int>)

/**
 * A path is valid on [board] when it visits at least one tile, every index is on the board, no
 * tile is reused, and consecutive tiles are adjacent (dossier 1.1).
 */
fun Path.isValidOn(board: Board): Boolean =
  indices.isNotEmpty() &&
    indices.all { it in board.tiles.indices } &&
    indices.toSet().size == indices.size &&
    indices.zipWithNext().all { (from, to) -> board.areAdjacent(from, to) }

/** The word spelled by concatenating the letters of every tile on the path, in order. */
fun Path.spell(board: Board): String = indices.joinToString(separator = "") { board.tiles[it].letters }

/**
 * Every word this path can spell (ADR 0015): the cartesian product of each tile's [Tile.options],
 * in order. A plain path (no alternatives tile on it) returns a single-element list, the same word
 * [spell] would build. A board only ever carries a handful of alternatives tiles (the generator
 * places at most one per round), so this product stays small; nothing here bounds it explicitly.
 *
 * Order matters to [br.com.colman.palavramento.domain.submission.SubmissionValidator]: earlier tiles
 * vary slower than later ones, the same order a nested loop over each tile's options would produce.
 */
fun Path.spellings(board: Board): List<String> {
  var results = listOf("")
  for (index in indices) {
    val options = board.tiles[index].options
    results = results.flatMap { prefix -> options.map { option -> prefix + option } }
  }
  return results
}
