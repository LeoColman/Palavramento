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
    // Explicit rather than relying on Ktor's own default (task brief 4): a non-2xx REST response
    // throws ClientRequestException/ServerResponseException, which is what lets
    // br.com.colman.palavramento.data.AuthController tell a rejected token/credentials apart from a
    // network failure by inspecting the response status.
    expectSuccess = true
    install(WebSockets)
    install(ContentNegotiation) {
      json(PalavramentoJson)
    }
  }
}
