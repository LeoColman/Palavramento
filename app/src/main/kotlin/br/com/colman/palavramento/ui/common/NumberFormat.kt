// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.common

import kotlin.math.round

private const val DecimalScale = 10.0

/**
 * One decimal place, the precision every average on screen uses (dossier 6.3 fixes it for
 * "Segundos por palavra"). Round stats already arrive rounded from `:domain`, but lifetime averages
 * come from the server as raw doubles, which rendered as "12.333333333333334" in the lobby.
 */
fun Double.oneDecimal(): String =
  // `+ 0.0` turns a negative zero (a tiny negative that rounds away) into "0.0" instead of "-0.0".
  (round(this * DecimalScale) / DecimalScale + 0.0).toString()
