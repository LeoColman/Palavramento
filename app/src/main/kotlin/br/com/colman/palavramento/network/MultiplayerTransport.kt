// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.network

import br.com.colman.palavramento.domain.protocol.ClientMessage
import br.com.colman.palavramento.domain.protocol.ServerMessage
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over one `/ws/multiplayer` connection (dossier 5). [MultiplayerSession] drives the
 * clock-sync handshake, `JoinRoom` and reconnect logic against this interface, so that logic is
 * unit-testable on the JVM with a fake transport, independent of Ktor and the Android runtime.
 */
interface MultiplayerTransport {

  /** Opens the connection. Throws on failure; the caller decides whether/when to retry. */
  suspend fun connect()

  /**
   * Messages received after [connect]. A single logical stream for the lifetime of the connection:
   * collecting it more than once concurrently is not supported, matching a `ReceiveChannel`'s
   * single-consumer contract. Completes when the connection closes, normally or not.
   */
  fun incoming(): Flow<ServerMessage>

  /** Sends [message] over the current connection. */
  suspend fun send(message: ClientMessage)

  /** Closes the connection, if open. Safe to call more than once. */
  suspend fun close()
}
