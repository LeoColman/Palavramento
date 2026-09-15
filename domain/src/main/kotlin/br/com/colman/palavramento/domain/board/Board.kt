// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.board

/**
 * A square grid of [Tile]s, indexed row-major (`index = row * size + col`).
 *
 * The shipped game uses a 4x4 grid, but the property tests in dossier 10 run on 3x3 boards, so
 * [size] is a constructor parameter rather than a constant.
 */
data class Board(val size: Int, val tiles: List<Tile>) {
  init {
    require(size > 0) { "Board size must be positive, got $size" }
    require(tiles.size == size * size) {
      "Expected ${size * size} tiles for a ${size}x$size board, got ${tiles.size}"
    }
  }

  /** Row of [index], 0-based. */
  fun rowOf(index: Int): Int = index / size

  /** Column of [index], 0-based. */
  fun colOf(index: Int): Int = index % size

  /** Index for a (row, col) pair. Inverse of [rowOf]/[colOf]. */
  fun indexOf(row: Int, col: Int): Int = row * size + col

  /**
   * Indices of the up-to-8 tiles adjacent to [index] (orthogonal and diagonal, dossier 1.1).
   *
   * Computed once per board and cached: the solver walks this for every step of every DFS branch,
   * and recomputing the 8 neighbor offsets on every call would allocate far more than needed.
   */
  fun neighborsOf(index: Int): IntArray = adjacency[index]

  /** True when [a] and [b] are different tiles that are adjacent to each other. */
  fun areAdjacent(a: Int, b: Int): Boolean = b in neighborsOf(a)

  private val adjacency: Array<IntArray> by lazy {
    Array(tiles.size) { index -> computeNeighbors(index) }
  }

  private fun computeNeighbors(index: Int): IntArray {
    val row = rowOf(index)
    val col = colOf(index)
    val result = ArrayList<Int>(MaxNeighbors)
    for (deltaRow in -1..1) {
      for (deltaCol in -1..1) {
        if (deltaRow == 0 && deltaCol == 0) continue
        val neighborRow = row + deltaRow
        val neighborCol = col + deltaCol
        if (neighborRow in 0 until size && neighborCol in 0 until size) {
          result += indexOf(neighborRow, neighborCol)
        }
      }
    }
    return result.toIntArray()
  }

  companion object {
    private const val MaxNeighbors = 8
  }
}
