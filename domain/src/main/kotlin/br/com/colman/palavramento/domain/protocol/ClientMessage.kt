// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Every message a client can send over `/ws/multiplayer` (dossier 5.2). */
@Serializable
sealed interface ClientMessage {

  /**
   * Joins the room's current or next round. [sessionToken] is an addition beyond the dossier's
   * payload table: 5.3's prose says a reconnecting client "reenvia JoinRoom com o token", so the
   * token needs a field on this message even though the table only lists [languageCode]. Null for a
   * brand new guest session. Documented in `docs/adr/0005-protocolo.md`.
   */
  @Serializable
  @SerialName("JoinRoom")
  data class JoinRoom(val languageCode: String = "pt-BR", val sessionToken: String? = null) : ClientMessage

  @Serializable
  @SerialName("SubmitWord")
  data class SubmitWord(val roundId: String, val path: List<Int>, val clientTimestamp: Long) : ClientMessage

  @Serializable
  @SerialName("LeaveRoom")
  object LeaveRoom : ClientMessage

  /**
   * Clock sync request: [clientSentAt] is echoed back by [ServerMessage.ClockSyncResponse] together
   * with the server's own clock, so the client can compute a round-trip-corrected offset. Addition
   * beyond the dossier, required by 5.3; see `docs/adr/0005-protocolo.md`.
   */
  @Serializable
  @SerialName("ClockSync")
  data class ClockSync(val clientSentAt: Long) : ClientMessage
}
