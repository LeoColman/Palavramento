// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.game

import br.com.colman.palavramento.domain.board.Board

/**
 * Pure state machine driving the continuous drag-to-trace gesture (dossier 6.2): entering a tile's
 * hit radius appends it when it is adjacent to the last tile and not already in the path;
 * re-entering the second-to-last tile undoes the last append (natural backtrack); every other tile
 * is ignored. [board] is only used for its adjacency graph (`Board.areAdjacent`); it never decides
 * scoring.
 *
 * Kept free of Compose and Android so the drag rules can be property-tested on the JVM
 * (`app/src/test`) and reused, unmodified, by the gesture composable.
 */
class PathTracer(private val board: Board) {
  private val current = mutableListOf<Int>()

  /** Logical tile indices traced so far, in order. Always adjacency-valid and repeat-free. */
  val path: List<Int> get() = current.toList()

  /** Starts a new trace at [index], discarding any in-progress path. */
  fun begin(index: Int) {
    current.clear()
    current.add(index)
  }

  /**
   * Called whenever the drag pointer enters tile [index]'s hit radius. Returns true when the path
   * changed as a result. A single `when` expression, rather than early returns, so there is exactly
   * one place that decides the outcome.
   */
  fun onTileEntered(index: Int): Boolean = when {
    current.isEmpty() -> {
      begin(index)
      true
    }

    index == current.last() -> false

    current.size >= UndoMinSize && index == current[current.size - UndoOffset] -> {
      current.removeAt(current.size - 1)
      true
    }

    index in current -> false
    !board.areAdjacent(current.last(), index) -> false

    else -> {
      current.add(index)
      true
    }
  }

  /** Clears the in-progress path, e.g. after the pointer is released and the word is submitted. */
  fun clear() = current.clear()

  private companion object {
    const val UndoMinSize = 2
    const val UndoOffset = 2
  }
}
