// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Rotation
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.board.logicalIndexAt
import br.com.colman.palavramento.domain.submission.RejectionReason
import br.com.colman.palavramento.game.PathTracer
import br.com.colman.palavramento.state.SubmissionFeedback
import br.com.colman.palavramento.ui.common.animationDurationMillis
import br.com.colman.palavramento.ui.theme.PalavramentoColors
import kotlinx.coroutines.delay
import kotlin.math.sqrt

/** Semantics test tag for the board's drag surface, used by the Compose gesture tests. */
const val BoardTestTag = "board"

/** Semantics test tag for the in-progress traced word, used by the Compose gesture tests. */
const val TracedWordTestTag = "tracedWord"

/**
 * The 4x4 board (dossier 6.2): square orange tiles, value top-left, letter (or digraph, ADR 0012)
 * centered, and the continuous drag-to-trace gesture (dossier 6.2, task brief 1): the pointer's
 * first tile is registered right at touch-down (see the
 * `awaitFirstDown`/[drag] gesture below, which skips `detectDragGestures`' touch-slop gate so the
 * first tile is never missed or misplaced); entering a tile's hit radius (40% of the tile size from
 * its center) after that appends it when adjacent to the last tile and unused; re-entering the
 * second-to-last tile undoes the last append; anything else is ignored; releasing submits the path
 * (single-tile paths ignored). The traced path is also drawn as a line over the tiles in display
 * space (task brief 1), and traced/flashed tiles animate smoothly (task brief 2), both skipped when
 * [br.com.colman.palavramento.ui.common.LocalAnimationsEnabled] is off.
 *
 * [rotation] only changes which logical tile is drawn at which display cell
 * ([br.com.colman.palavramento.domain.board.logicalIndexAt]); [onSubmit] always receives logical
 * indices, never display ones. [visualRotationDegrees] is a purely cosmetic spin overlay (task brief
 * 2: "Girar" animates instead of snapping) applied on top of that already-correct arrangement; it
 * never feeds back into which tile is at which cell.
 *
 * [feedback] flashes the tiles of the last submitted path (task brief 2: accept/reject color, reject
 * also shakes); [hapticsEnabled] gates the light tick fired when a tile is appended (task brief 3).
 */
@Composable
fun BoardView(
  tiles: List<Tile>,
  rotation: Rotation,
  onSubmit: (List<Int>) -> Unit,
  modifier: Modifier = Modifier,
  visualRotationDegrees: Float = 0f,
  feedback: SubmissionFeedback? = null,
  hapticsEnabled: Boolean = true,
) {
  val colors = PalavramentoColors.current
  val gridSize = remember(tiles) { sqrt(tiles.size.toDouble()).toInt() }
  val board = remember(tiles) { Board(gridSize, tiles) }
  val tracer = remember(tiles) { PathTracer(board) }
  var path by remember(tiles) { mutableStateOf(emptyList<Int>()) }
  var boxSize by remember { mutableStateOf(IntSize.Zero) }
  val haptics = LocalHapticFeedback.current
  val flashState = rememberTileFlashState(feedback)
  // Read through a State instead of straight off the parameter: the two handlers below are captured
  // by a pointerInput that only restarts when the board or its rotation changes, so a plain capture
  // would keep ticking with whatever the setting was back then - a toggle flipped mid-round
  // (MatchSettingsSheet) would only be obeyed after the next round or the next "Girar".
  val hapticsOn by rememberUpdatedState(hapticsEnabled)

  fun handlePointer(offset: Offset) {
    val displayIndex = displayIndexAt(offset, boxSize, gridSize) ?: return
    val logicalIndex = logicalIndexAt(displayIndex, gridSize, rotation)
    val sizeBefore = tracer.path.size
    if (tracer.onTileEntered(logicalIndex)) {
      path = tracer.path
      if (hapticsOn && tracer.path.size > sizeBefore) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
      }
    }
  }

  fun endDrag() {
    if (tracer.path.size >= MinSubmittablePathLength) {
      onSubmit(tracer.path)
      if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    tracer.clear()
    path = emptyList()
  }

  Column(modifier) {
    Box(
      Modifier
        .testTag(BoardTestTag)
        .fillMaxWidth()
        .aspectRatio(1f)
        .onSizeChanged { boxSize = it }
        .pointerInput(tiles, rotation) {
          awaitEachGesture {
            // No touch-slop gate (unlike detectDragGestures): the first tile is registered right
            // where the finger goes down, so a quick tap-and-drag never loses or misplaces it.
            val down = awaitFirstDown(requireUnconsumed = false)
            handlePointer(down.position)
            down.consume()
            drag(down.id) { change ->
              handlePointer(change.position)
              change.consume()
            }
            endDrag()
          }
        },
    ) {
      Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = visualRotationDegrees }) {
        TileGrid(tiles, rotation, gridSize, path, flashState, Modifier.fillMaxSize())
        TracedPathOverlay(path, gridSize, rotation, colors.highlight, Modifier.matchParentSize())
      }
    }

    TracedWordLabel(path, tiles, colors.textPrimary)
  }
}

/** The tile grid itself (dossier 6.2): one [BoardTile] per display cell, in [rotation]'s arrangement. */
@Composable
private fun TileGrid(
  tiles: List<Tile>,
  rotation: Rotation,
  gridSize: Int,
  path: List<Int>,
  flashState: TileFlashState,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    repeat(gridSize) { row ->
      Row(Modifier.weight(1f).fillMaxWidth()) {
        repeat(gridSize) { col ->
          val displayIndex = row * gridSize + col
          val logicalIndex = logicalIndexAt(displayIndex, gridSize, rotation)
          val tile = tiles[logicalIndex]
          BoardTile(
            tile = tile,
            // The server bakes the round's mutator into each tile, so this is exactly what it scores.
            value = tile.value,
            isTraced = logicalIndex in path,
            flashKind = flashState.flash.kindFor(logicalIndex),
            shakeOffsetPx = flashState.shakeOffsetPx,
            modifier = Modifier.weight(1f).fillMaxWidth().aspectRatio(1f),
          )
        }
      }
    }
  }
}

/** The traced-path line (task brief 1), drawn in display space so it follows [rotation]. */
@Composable
private fun TracedPathOverlay(
  path: List<Int>,
  gridSize: Int,
  rotation: Rotation,
  color: Color,
  modifier: Modifier = Modifier,
) {
  val strokeWidthPx = with(LocalDensity.current) { PathStrokeWidth.toPx() }
  Canvas(modifier) {
    if (path.size < MinDrawablePathLength) return@Canvas
    val points = path.map { logicalIndex ->
      tileCenter(displayIndexOf(logicalIndex, gridSize, rotation), size, gridSize)
    }
    for (index in 0 until points.size - 1) {
      drawLine(color, points[index], points[index + 1], strokeWidth = strokeWidthPx, cap = StrokeCap.Round)
    }
  }
}

/**
 * Shows the tiles of the in-progress trace literally, e.g. "CA/FSA (24)" over an alternatives tile
 * (ADR 0015): kept simple on purpose, over resolving to the spelling that will actually match. This
 * label only exists while [path] is non-empty, i.e. mid-drag, before any verdict exists to resolve
 * against - [BoardView.endDrag] clears [path] the moment a path is released and submitted, the same
 * instant [feedback] would start telling the two options apart. So there is no point in this
 * component's lifetime where both a complete path and its verdict are available together; showing the
 * tiles as traced is the honest state of "not decided yet", not a compromise.
 */
@Composable
private fun TracedWordLabel(path: List<Int>, tiles: List<Tile>, color: Color) {
  val word = path.joinToString(separator = "") { tiles[it].letters }
  val score = path.sumOf { tiles[it].value }
  Text(
    text = if (word.isEmpty()) "" else "$word ($score)",
    modifier = Modifier.padding(top = 8.dp).testTag(TracedWordTestTag),
    color = color,
  )
}

/**
 * Which color a tile should flash for the last submission result (task brief 2), if any. A word the
 * player already found flashes yellow, not the error red: it is a valid word, just not new.
 */
private enum class TileFlashKind { None, Accepted, Rejected, Duplicate }

/** The tiles a `WordAccepted`/`WordRejected` path currently flashes, and which color. */
private data class TileFlash(val path: Set<Int>, val kind: TileFlashKind)

/** [TileFlash] in progress, if any, plus the current shake offset (task brief 2: reject shakes). */
private data class TileFlashState(val flash: TileFlash?, val shakeOffsetPx: Float)

/**
 * Drives [TileFlash]/shake from [feedback] (task brief 2): flashes the submitted path's tiles the
 * accept/reject color, shakes on reject, then clears after [FlashDurationMillis].
 *
 * Only the motion honors [br.com.colman.palavramento.ui.common.LocalAnimationsEnabled]: the shake,
 * and the tween the tile color rides in on. The flash itself always lasts [FlashDurationMillis],
 * because it is the verdict on the word, not decoration.
 */
@Composable
private fun rememberTileFlashState(feedback: SubmissionFeedback?): TileFlashState {
  var flash by remember { mutableStateOf<TileFlash?>(null) }
  val shakeOffsetPx = remember { Animatable(0f) }
  val shakeStepDurationMs = animationDurationMillis(ShakeStepMillis)

  LaunchedEffect(feedback) {
    val current = feedback ?: return@LaunchedEffect
    val flashedPath = current.pathOrEmpty().toSet()
    if (flashedPath.isEmpty()) return@LaunchedEffect
    val kind = when (current) {
      is SubmissionFeedback.Accepted -> TileFlashKind.Accepted
      is SubmissionFeedback.Rejected ->
        if (current.reason == RejectionReason.AlreadyFound) TileFlashKind.Duplicate else TileFlashKind.Rejected
    }
    flash = TileFlash(flashedPath, kind)
    // Only a real mistake shakes; a repeated word just flashes.
    if (kind == TileFlashKind.Rejected) {
      if (shakeStepDurationMs > 0) {
        for (offset in ShakeOffsetsPx) shakeOffsetPx.animateTo(offset, tween(shakeStepDurationMs))
      }
      shakeOffsetPx.snapTo(0f)
    }
    // Deliberately not animationDurationMillis: how long the color stays is not an animation, it is
    // the answer to "was my word good?". Running it through the system "remove animations" setting
    // set the flash and cleared it in the same frame, so a player with that setting on, or with a
    // battery saver that forces it, found words and never saw the board turn green.
    // Deliberately not animationDurationMillis: how long the color stays is not an animation, it is
    // the answer to "was my word good?". Running it through the system "remove animations" setting
    // set the flash and cleared it in the same frame, so a player with that setting on, or with a
    // battery saver that forces it, found words and never saw the board turn green.
    delay(FlashDurationMillis.toLong())
    flash = null
  }

  return TileFlashState(flash, shakeOffsetPx.value)
}

private fun TileFlash?.kindFor(logicalIndex: Int): TileFlashKind {
  if (this == null || logicalIndex !in path) return TileFlashKind.None
  return kind
}

private fun SubmissionFeedback.pathOrEmpty(): List<Int> = when (this) {
  is SubmissionFeedback.Accepted -> path
  is SubmissionFeedback.Rejected -> path
}

@Composable
private fun BoardTile(
  tile: Tile,
  value: Int,
  isTraced: Boolean,
  flashKind: TileFlashKind,
  shakeOffsetPx: Float,
  modifier: Modifier = Modifier,
) {
  val colors = PalavramentoColors.current
  val targetColor = when {
    flashKind == TileFlashKind.Accepted -> colors.accepted
    flashKind == TileFlashKind.Rejected -> colors.rejected
    flashKind == TileFlashKind.Duplicate -> colors.duplicate
    isTraced -> colors.highlight
    else -> colors.tileBackground
  }
  val colorDurationMs = animationDurationMillis(TileColorAnimationMillis)
  val background by animateColorAsState(targetColor, tween(colorDurationMs), label = "tileColor")
  val scaleDurationMs = animationDurationMillis(TileScaleAnimationMillis)
  val scale by animateFloatAsState(if (isTraced) TracedScale else 1f, tween(scaleDurationMs), label = "tileScale")
  val shakeX = if (flashKind == TileFlashKind.Rejected) shakeOffsetPx else 0f

  BoxWithConstraints(
    modifier
      .padding(TileGap)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
        translationX = shakeX
      }
      .background(background, RoundedCornerShape(TileCornerRadius)),
  ) {
    // Text sizes follow the tile's own height instead of fixed sp: letters fill a big tile on any
    // screen, and a large system font scale cannot push them past the tile's edges. A digraph tile
    // (ADR 0012, e.g. "QU") has two characters, so it gets a smaller fraction to keep both clear; an
    // alternatives tile (ADR 0015, e.g. "A/F") has three, so it gets a slightly smaller one still.
    val density = LocalDensity.current
    val letterFraction = letterHeightFractionFor(tile)
    val letterSize = with(density) { (maxHeight * letterFraction).toSp() }
    val valueSize = with(density) { (maxHeight * ValueHeightFraction).toSp() }
    Text(
      text = value.toString(),
      color = colors.tileValueText,
      fontSize = valueSize,
      modifier = Modifier.align(Alignment.TopStart).padding(TileValuePadding),
    )
    Text(
      text = tile.letters,
      color = colors.tileText,
      fontSize = letterSize,
      modifier = Modifier.align(Alignment.Center),
    )
  }
}

/**
 * The letter-height fraction for [tile] (see the comment at its call site): a plain tile (one
 * character) gets [LetterHeightFraction], a digraph (two characters, ADR 0012) gets
 * [MultiLetterHeightFraction], and an alternatives tile (three characters counting the `/`, ADR
 * 0015, e.g. "A/F") gets the smallest, [AlternativesHeightFraction], so its extra character still
 * fits without shrinking a plain or digraph tile's letters unnecessarily.
 */
private fun letterHeightFractionFor(tile: Tile): Float = when {
  tile.letters.contains('/') -> AlternativesHeightFraction
  tile.letters.length > 1 -> MultiLetterHeightFraction
  else -> LetterHeightFraction
}

private const val MinSubmittablePathLength = 2
private const val MinDrawablePathLength = 2
private const val TracedScale = 1.06f
private const val TileColorAnimationMillis = 150
private const val TileScaleAnimationMillis = 120
private const val FlashDurationMillis = 500
private const val ShakeStepMillis = 40
private val ShakeOffsetsPx = listOf(-16f, 16f, -12f, 12f, -6f, 0f)
private val TileGap = 3.dp
private val TileCornerRadius = 8.dp
private val TileValuePadding = PaddingValues(4.dp)
private val PathStrokeWidth = 6.dp
private const val LetterHeightFraction = 0.4f
private const val MultiLetterHeightFraction = 0.3f
private const val AlternativesHeightFraction = 0.26f
private const val ValueHeightFraction = 0.19f
