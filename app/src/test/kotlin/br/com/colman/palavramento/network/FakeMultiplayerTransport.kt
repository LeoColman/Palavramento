// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.network

import br.com.colman.palavramento.domain.protocol.ClientMessage
import br.com.colman.palavramento.domain.protocol.ServerMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * In-memory [MultiplayerTransport] for [MultiplayerSessionTest]: [connect] opens a fresh channel
 * each time, simulating a brand new socket on every (re)connection attempt.
 */
class FakeMultiplayerTransport : MultiplayerTransport {
  val sent = mutableListOf<ClientMessage>()

  var connectCount = 0
    private set

  /** Set to make the next [connect] fail once, then clear itself. */
  var connectFailure: Throwable? = null

  private var channel = Channel<ServerMessage>(Channel.UNLIMITED)

  override suspend fun connect() {
    connectCount++
    val failure = connectFailure
    if (failure != null) {
      connectFailure = null
      throw failure
    }
    channel = Channel(Channel.UNLIMITED)
  }

  override fun incoming(): Flow<ServerMessage> = channel.receiveAsFlow()

  override suspend fun send(message: ClientMessage) {
    sent += message
  }

  override suspend fun close() {
    channel.close()
  }

  /** Simulates a message arriving from the server. */
  fun push(message: ServerMessage) {
    channel.trySend(message)
  }

  /** Simulates the connection dropping: the current `incoming()` collector completes normally. */
  fun dropConnection() {
    channel.close()
  }
}
