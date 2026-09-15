// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

/**
 * Parses a Hunspell `.dic` file: a first line with the entry count (ignored here, `lines.size - 1`
 * is used instead so a miscounted header never drops or fabricates an entry), then one `stem` or
 * `stem/flags` per line. VERO's `pt_BR.dic` starts with a UTF-8 BOM and has no morphological fields
 * (dossier §11 phase 1), so neither is handled here.
 */
object DicFileParser {
  fun parse(lines: List<String>): List<DicEntry> = lines.drop(1).mapNotNull { line ->
    if (line.isEmpty()) return@mapNotNull null
    val slash = line.indexOf('/')
    if (slash == -1) {
      DicEntry(line, emptySet())
    } else {
      DicEntry(line.substring(0, slash), line.substring(slash + 1).toSet())
    }
  }
}
