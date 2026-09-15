// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.lexicon

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/** Sentinel for [EntryTable.rank]: the node does not spell a complete word. */
private const val NoEntry = -1

/** Sentinel for [EntryTable.displayIndex]: the display form is the lowercase spelling. */
private const val NoOverride = -1

/** Difference between the code of a lowercase and an uppercase ASCII letter, e.g. 'a'.code - 'A'.code. */
private const val AsciiCaseOffset = 32

private val Magic = byteArrayOf('P'.code.toByte(), 'L'.code.toByte(), 'E'.code.toByte(), 'X'.code.toByte())
private const val FormatVersion = 1

/**
 * The trie's shape: nodes are numbered breadth-first so that a node's children occupy a contiguous
 * range of node ids, `childStart[node] until childStart[node + 1]`. [letter] holds, for every node
 * but the root, the character of the edge that leads to it from its parent, sorted within each
 * sibling group. Because children occupy a contiguous id range, that range doubles as the set of
 * target node ids, so no separate "target node" array is needed. [parent] is kept only to walk back
 * up to the root when reconstructing a default display string (see [EntryTable]).
 */
private class NodeShape(val childStart: IntArray, val letter: ByteArray, val parent: IntArray) {
  val nodeCount: Int get() = letter.size
}

/**
 * Per-node scoring payload, parallel to [NodeShape]. [rank] is [NoEntry] for nodes that are not a
 * complete word. Display strings are stored only when they differ from the lowercase of the node's
 * own spelling: most Portuguese forms display exactly as their normalized form lowercased
 * (`casa` -> `casa`), so only accented/cedilla forms (`pêssego`, `ação`) need an override in
 * [overrideOffset]/[overrideBytes], which are packed as one UTF-8 blob plus offsets rather than as
 * one String object per entry, to keep both memory and load time down for a 1M+ form lexicon.
 */
private class EntryTable(
  val rank: IntArray,
  val displayIndex: IntArray,
  val overrideOffset: IntArray,
  val overrideBytes: ByteArray,
) {
  fun displayOf(index: Int): String {
    val start = overrideOffset[index]
    val end = overrideOffset[index + 1]
    return String(overrideBytes, start, end - start, StandardCharsets.UTF_8)
  }
}

/**
 * Array-backed [Lexicon], compact enough to hold the whole pt-BR lexicon (1M+ forms) in memory and
 * load in well under a second from the artifact produced at build time (dossier §2.4).
 */
class TrieLexicon private constructor(private val nodes: NodeShape, private val entries: EntryTable) : Lexicon {

  /** Number of trie nodes, root included. Exposed for measurement and reporting, not gameplay. */
  val nodeCount: Int get() = nodes.nodeCount

  override fun child(node: Int, letter: Char): Int {
    val target = letter.code.toByte()
    var index = nodes.childStart[node]
    val end = nodes.childStart[node + 1]
    while (index < end) {
      if (nodes.letter[index] == target) return index
      index++
    }
    return Lexicon.NoNode
  }

  override fun entry(node: Int): LexiconEntry? {
    val frequencyRank = entries.rank[node]
    if (frequencyRank == NoEntry) return null
    val override = entries.displayIndex[node]
    val display = if (override == NoOverride) defaultDisplayOf(node, nodes) else entries.displayOf(override)
    return LexiconEntry(display, frequencyRank)
  }

  /** Writes the binary artifact. Does not close [output]; the caller owns that stream's lifecycle. */
  fun write(output: OutputStream) {
    val data = DataOutputStream(output)
    data.write(Magic)
    data.writeInt(FormatVersion)
    data.writeInt(nodeCount)
    data.writeIntArray(nodes.childStart)
    data.write(nodes.letter)
    data.writeIntArray(entries.rank)
    data.writeIntArray(entries.displayIndex)
    data.writeIntArray(nodes.parent)
    data.writeInt(entries.overrideOffset.size)
    data.writeIntArray(entries.overrideOffset)
    data.writeInt(entries.overrideBytes.size)
    data.write(entries.overrideBytes)
    data.flush()
  }

  companion object {
    /** Builds a [TrieLexicon] from normalized (A-Z) forms to their scoring entry. */
    fun build(entries: Map<String, LexiconEntry>): TrieLexicon {
      val builder = Builder()
      entries.forEach { (normalized, entry) -> builder.insert(normalized, entry) }
      return builder.build()
    }

    /** Reads back an artifact written by [write]. Does not close [input]. */
    fun read(input: InputStream): TrieLexicon {
      val data = DataInputStream(input)
      val magic = ByteArray(Magic.size)
      data.readFully(magic)
      require(magic.contentEquals(Magic)) { "Not a Palavramento lexicon artifact (bad magic header)" }
      val version = data.readInt()
      require(version == FormatVersion) { "Unsupported lexicon artifact version $version" }

      val nodeCount = data.readInt()
      val childStart = data.readIntArray(nodeCount + 1)
      val letter = ByteArray(nodeCount)
      data.readFully(letter)
      val rank = data.readIntArray(nodeCount)
      val displayIndex = data.readIntArray(nodeCount)
      val parent = data.readIntArray(nodeCount)
      val overrideOffsetSize = data.readInt()
      val overrideOffset = data.readIntArray(overrideOffsetSize)
      val overrideBytesLength = data.readInt()
      val overrideBytes = ByteArray(overrideBytesLength)
      data.readFully(overrideBytes)

      return TrieLexicon(
        NodeShape(childStart, letter, parent),
        EntryTable(rank, displayIndex, overrideOffset, overrideBytes),
      )
    }
  }

  /** Mutable intermediate trie used only while building, renumbered breadth-first by [build]. */
  private class Builder {
    private val children = mutableListOf(LinkedHashMap<Char, Int>())
    private val entries = mutableListOf<LexiconEntry?>(null)

    fun insert(normalized: String, entry: LexiconEntry) {
      require(normalized.isNotEmpty() && normalized.all { it in 'A'..'Z' }) {
        "Lexicon keys must be normalized A-Z words, got '$normalized'"
      }
      var node = Lexicon.Root
      for (char in normalized) {
        node = children[node].getOrPut(char) {
          children += LinkedHashMap()
          entries += null
          children.lastIndex
        }
      }
      entries[node] = entry
    }

    fun build(): TrieLexicon {
      val n = children.size
      val order = IntArray(n)
      order[Lexicon.Root] = Lexicon.Root
      val parent = IntArray(n)
      val letter = ByteArray(n)
      val childStart = IntArray(n + 1)

      var discovered = 1
      for (currentNewId in 0 until n) {
        val currentOldId = order[currentNewId]
        childStart[currentNewId] = discovered
        for ((char, childOldId) in children[currentOldId].entries.sortedBy { it.key }) {
          val newId = discovered++
          order[newId] = childOldId
          parent[newId] = currentNewId
          letter[newId] = char.code.toByte()
        }
      }
      childStart[n] = discovered
      check(discovered == n) { "Every trie node must be discovered exactly once" }
      val nodeShape = NodeShape(childStart, letter, parent)

      val rank = IntArray(n) { NoEntry }
      val displayIndex = IntArray(n) { NoOverride }
      val overrides = mutableListOf<String>()
      for (newId in 0 until n) {
        val entry = entries[order[newId]] ?: continue
        rank[newId] = entry.frequencyRank
        if (entry.display != defaultDisplayOf(newId, nodeShape)) {
          displayIndex[newId] = overrides.size
          overrides += entry.display
        }
      }

      val (overrideOffset, overrideBytes) = packStrings(overrides)
      return TrieLexicon(nodeShape, EntryTable(rank, displayIndex, overrideOffset, overrideBytes))
    }
  }
}

/**
 * Reconstructs the lowercase spelling of [node] by walking parent pointers up to the root. Used both
 * at build time, to decide whether an entry needs a display override, and at read time, to answer
 * [Lexicon.entry] for nodes that do not need one.
 */
private fun defaultDisplayOf(node: Int, nodes: NodeShape): String {
  val builder = StringBuilder()
  var current = node
  while (current != Lexicon.Root) {
    builder.append((nodes.letter[current] + AsciiCaseOffset).toChar())
    current = nodes.parent[current]
  }
  return builder.reverse().toString()
}

/** Packs strings into one UTF-8 byte blob plus offsets, avoiding one String object per entry. */
private fun packStrings(values: List<String>): Pair<IntArray, ByteArray> {
  val offsets = IntArray(values.size + 1)
  val chunks = values.map { it.toByteArray(StandardCharsets.UTF_8) }
  var total = 0
  for (index in chunks.indices) {
    offsets[index] = total
    total += chunks[index].size
  }
  offsets[chunks.size] = total
  val bytes = ByteArray(total)
  var position = 0
  for (chunk in chunks) {
    chunk.copyInto(bytes, position)
    position += chunk.size
  }
  return offsets to bytes
}

private fun DataOutputStream.writeIntArray(array: IntArray) {
  val bytes = ByteArray(array.size * Int.SIZE_BYTES)
  ByteBuffer.wrap(bytes).asIntBuffer().put(array)
  write(bytes)
}

private fun DataInputStream.readIntArray(size: Int): IntArray {
  val bytes = ByteArray(size * Int.SIZE_BYTES)
  readFully(bytes)
  val array = IntArray(size)
  ByteBuffer.wrap(bytes).asIntBuffer().get(array)
  return array
}
