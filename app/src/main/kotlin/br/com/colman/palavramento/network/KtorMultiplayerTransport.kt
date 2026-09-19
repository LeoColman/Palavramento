// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.network

import android.util.Log
import br.com.colman.palavramento.domain.protocol.ClientMessage
import br.com.colman.palavramento.domain.protocol.PalavramentoJson
import br.com.colman.palavramento.domain.protocol.ServerMessage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.SerializationException

/**
 * Real [MultiplayerTransport] over Ktor's WebSocket client (task brief: Ktor + OkHttp + WebSockets,
 * `PalavramentoJson` for encoding). [baseUrl] is the `ws://`/`wss://` scheme and host; the path is
 * fixed to `/ws/multiplayer` (dossier 5).
 */
class KtorMultiplayerTransport(private val client: HttpClient, httpBaseUrl: String) : MultiplayerTransport {

  // The client is configured with one base URL (BuildConfig.SERVER_URL, an http(s) URL shared with
  // RestApi) so callers never juggle two settings for one server; the socket just needs its own
  // scheme.
  private val webSocketBaseUrl = httpBaseUrl
    .replaceFirst("https://", "wss://")
    .replaceFirst("http://", "ws://")

  private var session: DefaultClientWebSocketSession? = null

  override suspend fun connect() {
    session = client.webSocketSession(urlString = "$webSocketBaseUrl/ws/multiplayer")
  }

  override fun incoming(): Flow<ServerMessage> {
    val activeSession = requireNotNull(session) { "connect() was not called" }
    return activeSession.incoming.receiveAsFlow()
      .mapNotNull { it as? Frame.Text }
      .mapNotNull { decodeServerMessageOrNull(it.readText()) }
  }

  override suspend fun send(message: ClientMessage) {
    val activeSession = requireNotNull(session) { "connect() was not called" }
    activeSession.send(Frame.Text(PalavramentoJson.encodeToString(ClientMessage.serializer(), message)))
  }

  override suspend fun close() {
    session?.close()
    session = null
  }
}

private const val Tag = "MultiplayerTransport"

/**
 * Decodes one frame, or null with a log line when this build cannot read it at all (ADR 0018).
 * `PalavramentoJson` already turns an unknown message type and an unknown mutator into their
 * `Unknown` members, so what reaches here is a payload whose shape changed. Dropping that one frame
 * keeps the session: letting it fail the flow would close the socket and reconnect to the same
 * server, which would send the same frame again.
 */
internal fun decodeServerMessageOrNull(text: String): ServerMessage? = try {
  PalavramentoJson.decodeFromString(ServerMessage.serializer(), text)
} catch (failure: SerializationException) {
  Log.w(Tag, "Skipping a server message this build cannot decode: ${failure.message}")
  null
}
