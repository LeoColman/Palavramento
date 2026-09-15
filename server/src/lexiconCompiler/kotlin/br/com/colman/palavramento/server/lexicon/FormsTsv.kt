// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.lexicon

import br.com.colman.palavramento.domain.lexicon.LexiconEntry
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Writes the intermediate `forms.tsv` (dossier §2.2): `forma_canonica \t forma_normalizada \t
 * frequencia_rank`, one line per surviving canonical form. An unranked form ("especialista", dossier
 * §1.7) writes [LexiconEntry.Unranked] literally, so every row's rank column parses as a plain `Int`.
 */
object FormsTsv {
  private const val Separator = '\t'

  fun write(rows: List<FormRow>, file: File) {
    file.parentFile?.mkdirs()
    file.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
      for (row in rows) {
        writer.append(row.canonical).append(Separator).append(row.normalized).append(Separator)
        writer.append((row.rank ?: LexiconEntry.Unranked).toString()).append('\n')
      }
    }
  }
}
