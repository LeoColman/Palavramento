// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.protocol

import br.com.colman.palavramento.domain.mutator.Mutator
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

/**
 * Forward compatibility for the two hierarchies a server can extend on its own (ADR 0018): a `type`
 * this build has no case for decodes as [ServerMessage.Unknown] or [Mutator.Unknown] instead of
 * throwing. Deploys are server first, so without this an installed app stops decoding `RoundStart`
 * the moment a new mutator ships, and reconnects forever.
 */
private val ForwardCompatibleMessages = SerializersModule {
  polymorphicDefaultDeserializer(ServerMessage::class) { ServerMessage.Unknown.serializer() }
  polymorphicDefaultDeserializer(Mutator::class) { Mutator.Unknown.serializer() }
}

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
  serializersModule = ForwardCompatibleMessages
}
