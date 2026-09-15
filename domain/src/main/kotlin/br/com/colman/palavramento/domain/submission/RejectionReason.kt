// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.submission

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Why a [br.com.colman.palavramento.domain.board.Path] submission was rejected (dossier 5.1). The
 * dossier names these exact wire tokens for the `WordRejected` message, carried by [SerialName]
 * rather than by the Kotlin identifier.
 *
 * `BLOQUEADA_POR_MUTADOR` existed in v1 and was removed by ADR 0012 (product owner decision,
 * 2026-09-15) along with the `LETRA_PROIBIDA` mutator: no mutator blocks a word from scoring any more.
 */
@Serializable
enum class RejectionReason {
  @SerialName("INVALIDA")
  NotAWord,

  @SerialName("JA_ENCONTRADA")
  AlreadyFound,

  @SerialName("CAMINHO_INVALIDO")
  InvalidPath,

  @SerialName("CURTA")
  TooShort,
}
