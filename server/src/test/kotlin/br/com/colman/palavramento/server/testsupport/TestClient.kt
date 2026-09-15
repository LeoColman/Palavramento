// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.testsupport

import br.com.colman.palavramento.domain.protocol.ClientMessage
import br.com.colman.palavramento.domain.protocol.PalavramentoJson
import br.com.colman.palavramento.domain.protocol.ServerMessage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** An [HttpClient] with everything `/ws/multiplayer` and the REST endpoints need. */
fun ApplicationTestBuilder.testHttpClient(): HttpClient = createClient {
  install(WebSockets)
  install(ContentNegotiation) { json(PalavramentoJson) }
}

suspend fun DefaultClientWebSocketSession.sendClientMessage(message: ClientMessage) {
  send(Frame.Text(PalavramentoJson.encodeToString(message)))
}

/** Reads the next text frame as a [ServerMessage], skipping any non-text frame (pings, etc). */
suspend fun DefaultClientWebSocketSession.nextServerMessage(): ServerMessage {
  while (true) {
    val frame = incoming.receive()
    if (frame is Frame.Text) return PalavramentoJson.decodeFromString(frame.readText())
  }
}
