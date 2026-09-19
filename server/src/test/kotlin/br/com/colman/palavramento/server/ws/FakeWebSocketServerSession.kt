// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.ws

import io.ktor.server.application.ApplicationCall
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.utils.io.InternalAPI
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A bare [DefaultWebSocketServerSession] double: the real Ktor interface, no real network. [Connection]
 * only ever calls [send] (through [outgoing]) and the [io.ktor.websocket.close] extension, which also
 * goes through [send], so every other member here is unused filler needed only to satisfy the
 * interface. [sent] is what a [ConnectionTest] or [ConnectionRegistryTest] reads back to assert
 * exactly what was written to the wire.
 */
// terminate() overrides an internal Ktor member below; this test double never calls it itself.
@OptIn(InternalAPI::class)
class FakeWebSocketServerSession(outgoingCapacity: Int = Channel.UNLIMITED) : DefaultWebSocketServerSession {
  val sent = Channel<Frame>(outgoingCapacity)

  override val call: ApplicationCall get() = error("FakeWebSocketServerSession.call is not needed by Connection")
  override val coroutineContext: CoroutineContext = EmptyCoroutineContext
  override var masking: Boolean = false
  override var maxFrameSize: Long = Long.MAX_VALUE
  override val incoming: ReceiveChannel<Frame> = Channel()
  override val outgoing: SendChannel<Frame> = sent
  override val extensions: List<WebSocketExtension<*>> = emptyList()
  override var pingIntervalMillis: Long = -1
  override var timeoutMillis: Long = -1
  override val closeReason: Deferred<CloseReason?> = CompletableDeferred()

  override suspend fun send(frame: Frame) {
    outgoing.send(frame)
  }

  override suspend fun flush() {
    // Nothing buffered outside of `sent` to flush.
  }

  @Suppress("OVERRIDE_DEPRECATION") // overriding Ktor's own deprecated member; this test double never calls it itself.
  override fun terminate() {
    sent.close()
  }

  override fun start(negotiatedExtensions: List<WebSocketExtension<*>>) {
    // Never called by Connection: no real handshake to negotiate extensions for.
  }
}
