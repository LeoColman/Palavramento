// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import java.io.File
import java.nio.charset.StandardCharsets

private const val ByteOrderMark = '﻿'

/**
 * Reads a UTF-8 text file as lines, dropping a leading byte-order mark if present. Both
 * `pt_BR.dic` and `pt_BR.aff` start with one (dossier §11 phase 1).
 */
fun File.readLexiconLines(): List<String> {
  val text = readText(StandardCharsets.UTF_8)
  val withoutBom = if (text.startsWith(ByteOrderMark)) text.substring(1) else text
  return withoutBom.lineSequence().map { it.trimEnd('\r') }.toList()
}
