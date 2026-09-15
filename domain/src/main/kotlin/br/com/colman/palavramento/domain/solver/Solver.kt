// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.solver

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.lexicon.Lexicon
import br.com.colman.palavramento.domain.lexicon.LexiconEntry
import br.com.colman.palavramento.domain.lexicon.walk
import br.com.colman.palavramento.domain.mutator.DefaultMinimumLength

/**
 * Exhaustive board solver: depth-first search pruned by the lexicon's own prefix structure
 * (dossier 2.4). An instance holds no per-solve state, so it is safe to reuse across many boards.
 *
 * Every distinct normalized word reachable on the board is returned once, with its best-scoring
 * path (dossier: the same word only scores once per round, regardless of path), as long as it is at
 * least [DefaultMinimumLength] letters (ADR 0012: no mutator overrides that minimum any more). A
 * word's score is the sum of its tiles' own values: the generator already applied the round's
 * mutator to the tiles, so the solver never needs to know which mutator is active.
 *
 * The search itself avoids allocating anything beyond a few fixed-size buffers per [solve] call:
 * the path and visited buffers are reused across every DFS branch, and a word's normalized form and
 * [SolvedWord] are only built the moment a lexicon node turns out to be a real entry.
 */
class Solver(private val lexicon: Lexicon) {

  fun solve(board: Board, commonCutoff: Int = DefaultCommonCutoff): List<SolvedWord> {
    val buffers = Buffers(visited = BooleanArray(board.tiles.size), path = IntArray(board.tiles.size))
    val frame = SearchFrame(board, commonCutoff, buffers, best = LinkedHashMap())

    val start = Cursor(depth = 0, letterCount = 0, node = Lexicon.Root, score = 0)
    for (tileIndex in board.tiles.indices) {
      search(frame, tileIndex, start)
    }
    return frame.best.values.toList()
  }

  private fun search(frame: SearchFrame, tileIndex: Int, cursor: Cursor) {
    val tile = frame.board.tiles[tileIndex]
    val node = lexicon.walk(tile.letters, from = cursor.node)
    if (node == Lexicon.NoNode) return

    frame.buffers.path[cursor.depth] = tileIndex
    frame.buffers.visited[tileIndex] = true
    val next = Cursor(
      depth = cursor.depth + 1,
      letterCount = cursor.letterCount + tile.letters.length,
      node = node,
      score = cursor.score + tile.value,
    )

    val entry = lexicon.entry(node)
    if (entry != null && next.letterCount >= DefaultMinimumLength) {
      record(frame, next.depth, next.score, entry)
    }

    for (neighbor in frame.board.neighborsOf(tileIndex)) {
      if (!frame.buffers.visited[neighbor]) {
        search(frame, neighbor, next)
      }
    }

    frame.buffers.visited[tileIndex] = false
  }

  private fun record(frame: SearchFrame, length: Int, score: Int, entry: LexiconEntry) {
    val indices = frame.buffers.path.copyOfRange(0, length).toList()
    val normalized = indices.joinToString(separator = "") { frame.board.tiles[it].letters }
    val existing = frame.best[normalized]
    if (existing == null || score > existing.score) {
      val tier = if (entry.frequencyRank <= frame.commonCutoff) WordTier.Common else WordTier.Expert
      frame.best[normalized] = SolvedWord(normalized, entry.display, indices, score, tier)
    }
  }

  /** Reusable, mutable, fixed-size scratch space for one [solve] call's whole DFS tree. */
  private class Buffers(val visited: BooleanArray, val path: IntArray)

  /** Everything one [solve] call threads through every DFS branch, grouped to keep call sites short. */
  private class SearchFrame(
    val board: Board,
    val commonCutoff: Int,
    val buffers: Buffers,
    val best: MutableMap<String, SolvedWord>,
  )

  /** Immutable per-branch progress: what changes as [search] walks deeper, passed down by value. */
  private data class Cursor(val depth: Int, val letterCount: Int, val node: Int, val score: Int)

  companion object {
    /**
     * Common/expert cutoff (dossier 1.7) as a frequency rank. Calibrated against the real lexicon so
     * typical boards split close to 1:1 (docs/calibracao-letras.md); the server exposes it as config.
     */
    const val DefaultCommonCutoff = 300_000
  }
}
