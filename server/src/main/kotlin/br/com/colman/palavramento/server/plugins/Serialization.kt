// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.plugins

import br.com.colman.palavramento.domain.protocol.PalavramentoJson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

/** REST bodies use the exact same [PalavramentoJson] config as the WebSocket protocol (dossier §5). */
fun Application.configureSerialization() {
  install(ContentNegotiation) {
    json(PalavramentoJson)
  }
}
