// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.ws

import br.com.colman.palavramento.domain.protocol.PalavramentoJson
import br.com.colman.palavramento.domain.protocol.ServerMessage
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.serialization.encodeToString

/**
 * One open `/ws/multiplayer` socket, wrapped so
 * [br.com.colman.palavramento.server.round.RoomScheduler] never touches Ktor types directly.
 */
class Connection(private val session: DefaultWebSocketServerSession) {

  suspend fun send(message: ServerMessage) {
    session.send(Frame.Text(PalavramentoJson.encodeToString(message)))
  }

  suspend fun close(code: Short, reason: String) {
    session.close(CloseReason(code, reason))
  }
}

/** Close codes this server uses beyond the standard ones (dossier §5). */
object CloseCodes {
  /** Missing or invalid [br.com.colman.palavramento.domain.protocol.ClientMessage.JoinRoom.sessionToken]. */
  val InvalidToken: Short = CloseReason.Codes.VIOLATED_POLICY.code

  /** A newer connection for the same player took over (dossier phase 3 task: "the newest one wins"). */
  const val ReplacedByNewerConnection: Short = 4000

  /**
   * The account behind this socket's access token was just deleted (ADR 0020: "o socket aberto
   * cai"). A distinct code from [InvalidToken] (1008, VIOLATED_POLICY) on purpose: that one means
   * "your token is malformed or expired, get a new one", this one means "that account is gone, the
   * app should start over as a fresh guest" (ADR 0020's "entra como convidado novo").
   */
  const val AccountDeleted: Short = 4001
}
