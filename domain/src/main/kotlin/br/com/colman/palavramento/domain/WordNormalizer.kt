// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain

import java.text.Normalizer
import java.util.Locale

/**
 * Maps a lexicon form to the alphabet printed on tiles: no diacritics, upper case.
 *
 * `loâ` becomes `LOA`, `ação` becomes `ACAO`. Decomposing to NFD splits every accented letter into
 * its base letter plus combining marks, so dropping the marks covers ç, ã, ü and friends at once.
 */
object WordNormalizer {
  private val combiningMarks = Regex("\\p{Mn}+")

  fun normalize(word: String): String =
    combiningMarks.replace(Normalizer.normalize(word, Normalizer.Form.NFD), "").uppercase(Locale.ROOT)
}
