// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.network

import br.com.colman.palavramento.domain.protocol.PalavramentoJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json

/**
 * Builds the single [HttpClient] the app shares for REST calls and the `/ws/multiplayer` socket
 * (task brief: Ktor client, OkHttp engine, WebSockets, kotlinx.serialization with
 * [PalavramentoJson]).
 */
object HttpClientFactory {

  fun create(): HttpClient = HttpClient(OkHttp) {
    install(WebSockets)
    install(ContentNegotiation) {
      json(PalavramentoJson)
    }
  }
}
