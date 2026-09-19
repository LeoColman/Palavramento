// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.colman.palavramento.R
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.protocol.LabelledWord
import br.com.colman.palavramento.domain.solver.WordTier
import br.com.colman.palavramento.domain.stats.RoundStats
import br.com.colman.palavramento.ui.theme.PalavramentoColors
import kotlin.math.sqrt

/** Fixed width for a word row's score, so a long word never collides with it (orchestrator finding). */
private val ScoreSlotWidth = 24.dp

/** Results tab content (dossie 6.3): mini board, round stats, and the three word columns. */
@Composable
fun ResultsScreen(
  board: List<Tile>,
  stats: RoundStats,
  maxScore: Int,
  maxWords: Int,
  words: List<LabelledWord>,
) {
  val colors = PalavramentoColors.current
  Column(Modifier.fillMaxSize().background(colors.resultsPrimary).padding(16.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      MiniBoard(board, modifier = Modifier.weight(1f))
      StatsPanel(stats, maxScore, maxWords, modifier = Modifier.weight(2f))
    }
    Row(
      Modifier.fillMaxSize().padding(top = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      WordColumn(
        stringResource(R.string.results_column_found),
        words.filter { it.found }.sortedByDescending { it.score },
        Modifier.weight(1f),
      )
      WordColumn(
        stringResource(R.string.results_column_common),
        words.filter { !it.found && it.tier == WordTier.Common }.sortedByDescending { it.score },
        Modifier.weight(1f),
      )
      WordColumn(
        stringResource(R.string.results_column_expert),
        words.filter { !it.found && it.tier == WordTier.Expert }.sortedByDescending { it.score },
        Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun StatsPanel(stats: RoundStats, maxScore: Int, maxWords: Int, modifier: Modifier = Modifier) {
  val colors = PalavramentoColors.current
  Column(
    modifier
      .background(colors.surface, RoundedCornerShape(12.dp))
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    StatLine(stringResource(R.string.results_points_format, stats.points, maxScore))
    StatLine(stringResource(R.string.results_words_format, stats.words, maxWords))
    StatLine("${stringResource(R.string.results_seconds_per_word)}: ${stats.secondsPerWord}")
    StatLine("${stringResource(R.string.results_average_length)}: ${stats.averageLength}")
    StatLine("${stringResource(R.string.results_bonus_points)}: ${stats.bonusPoints}")
    StatLine("${stringResource(R.string.results_average_points)}: ${stats.averagePoints}")
    StatLine("${stringResource(R.string.results_xp)}: ${stats.xp}")
  }
}

@Composable
private fun StatLine(text: String) {
  Text(text, color = PalavramentoColors.current.textPrimary)
}

@Composable
private fun WordColumn(title: String, words: List<LabelledWord>, modifier: Modifier = Modifier) {
  val colors = PalavramentoColors.current
  Column(modifier.fillMaxHeight()) {
    Text(title, color = colors.textSecondary, fontSize = 12.sp)
    LazyColumn(Modifier.weight(1f)) {
      items(words, key = { it.word }) { word ->
        Row(
          Modifier.fillMaxWidth().padding(vertical = 2.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Orchestrator finding: a fixed-width slot for the score plus an ellipsized word is what
          // keeps a long word ("redepositamos") from colliding with its score at the small column
          // widths this three-column layout uses.
          Text(word.score.toString(), color = colors.textPrimary, modifier = Modifier.width(ScoreSlotWidth))
          Text(
            word.word,
            color = colors.textPrimary,
            fontStyle = if (word.tier == WordTier.Expert) FontStyle.Italic else FontStyle.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

@Composable
private fun MiniBoard(tiles: List<Tile>, modifier: Modifier = Modifier) {
  val colors = PalavramentoColors.current
  val gridSize = sqrt(tiles.size.toDouble()).toInt()
  Column(modifier.aspectRatio(1f)) {
    repeat(gridSize) { row ->
      Row(Modifier.weight(1f).fillMaxWidth()) {
        repeat(gridSize) { col ->
          val tile = tiles[row * gridSize + col]
          Box(
            Modifier
              .weight(1f)
              .fillMaxHeight()
              .padding(1.dp)
              .background(colors.tileBackground, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
          ) {
            // A digraph tile (ADR 0012, e.g. "QU") gets a smaller font, and an alternatives tile
            // (ADR 0015, e.g. "A/F") smaller still, same reasoning as BoardView.
            val fontSize = when {
              tile.letters.contains('/') -> MiniTileFontSizeAlternatives
              tile.letters.length > 1 -> MiniTileFontSizeMultiLetter
              else -> MiniTileFontSize
            }
            Text(tile.letters, color = colors.tileText, fontSize = fontSize)
          }
        }
      }
    }
  }
}

private val MiniTileFontSize = 9.sp
private val MiniTileFontSizeMultiLetter = 6.sp
private val MiniTileFontSizeAlternatives = 5.sp
