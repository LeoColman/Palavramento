// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.protocol

import kotlinx.serialization.json.Json

/**
 * Shared JSON configuration for every wire message (dossier 5). Every [ServerMessage] and
 * [ClientMessage] uses a `type` field as its polymorphic discriminator instead of a wrapper object,
 * unknown fields are ignored so a peer built against a newer or older protocol version does not
 * crash, and default values are always encoded so a new optional field is never silently dropped.
 */
val PalavramentoJson: Json = Json {
  classDiscriminator = "type"
  ignoreUnknownKeys = true
  encodeDefaults = true
}
