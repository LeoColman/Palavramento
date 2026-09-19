// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.ws

import br.com.colman.palavramento.domain.protocol.PalavramentoJson
import br.com.colman.palavramento.domain.protocol.ServerMessage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.serialization.encodeToString

/**
 * [Connection] wraps a Ktor session so [br.com.colman.palavramento.server.round.RoomScheduler] never
 * touches Ktor types directly. Driven against [FakeWebSocketServerSession] instead of a real socket,
 * asserting exactly what ends up on the wire.
 */
class ConnectionTest : FunSpec({

  test("send writes a single text frame with the message JSON-encoded the same way the wire protocol does") {
    val session = FakeWebSocketServerSession()
    val connection = Connection(session)
    val message: ServerMessage = ServerMessage.ClockSyncResponse(clientSentAt = 111L, serverTime = 222L)

    connection.send(message)

    val frame = session.sent.tryReceive().getOrThrow()
    frame.shouldBeInstanceOf<Frame.Text>()
    frame.readText() shouldBe PalavramentoJson.encodeToString(message)
  }

  test("close writes a single close frame carrying the exact code and reason given") {
    val session = FakeWebSocketServerSession()
    val connection = Connection(session)

    connection.close(CloseCodes.ReplacedByNewerConnection, "Replaced by a newer connection")

    val frame = session.sent.tryReceive().getOrThrow()
    frame.shouldBeInstanceOf<Frame.Close>()
    val reason = frame.readReason()
    reason.shouldNotBeNull()
    reason.code shouldBe CloseCodes.ReplacedByNewerConnection
    reason.message shouldBe "Replaced by a newer connection"
  }

  test("CloseCodes.InvalidToken is the standard policy-violation close code") {
    CloseCodes.InvalidToken shouldBe CloseReason.Codes.VIOLATED_POLICY.code
  }

  test("CloseCodes.ReplacedByNewerConnection is a distinct application close code") {
    CloseCodes.ReplacedByNewerConnection shouldBe 4000
  }
})
