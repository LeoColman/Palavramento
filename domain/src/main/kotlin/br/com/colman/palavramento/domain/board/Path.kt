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
