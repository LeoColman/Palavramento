// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.ws

import br.com.colman.palavramento.domain.protocol.PalavramentoJson
import br.com.colman.palavramento.domain.protocol.ServerMessage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.websocket.Frame
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.serialization.decodeFromString

/**
 * [ConnectionRegistry] against [FakeWebSocketServerSession] doubles: no Ktor server, no database,
 * just the in-memory "one live connection per player" bookkeeping (dossier phase 3 task: "if the
 * same player opens a second connection, the newest one wins").
 */
class ConnectionRegistryTest : FunSpec({

  test("sendTo delivers only to the named player's own connection") {
    val registry = ConnectionRegistry()
    val aliceSession = FakeWebSocketServerSession()
    val bobSession = FakeWebSocketServerSession()
    registry.register("alice", Connection(aliceSession))
    registry.register("bob", Connection(bobSession))
    val message = ServerMessage.LobbyState(nextRoundStartsAt = 1L, playersWaiting = 2)

    registry.sendTo("alice", message)

    val frame = aliceSession.sent.tryReceive().getOrThrow()
    frame.shouldBeInstanceOf<Frame.Text>()
    PalavramentoJson.decodeFromString<ServerMessage>(frame.readText()) shouldBe message
    bobSession.sent.tryReceive().isSuccess shouldBe false
  }

  test("sendTo to a player nobody registered is a silent no-op") {
    val registry = ConnectionRegistry()

    registry.sendTo("nobody", ServerMessage.LobbyState(nextRoundStartsAt = 1L, playersWaiting = 0))

    registry.connectedPlayerCount() shouldBe 0
  }

  test("registering a second connection for the same player closes the one it replaces") {
    val registry = ConnectionRegistry()
    val oldSession = FakeWebSocketServerSession()
    val newSession = FakeWebSocketServerSession()
    registry.register("alice", Connection(oldSession))

    registry.register("alice", Connection(newSession))

    val closeFrame = oldSession.sent.tryReceive().getOrThrow()
    closeFrame.shouldBeInstanceOf<Frame.Close>()
    val reason = closeFrame.readReason()
    reason.shouldNotBeNull()
    reason.code shouldBe CloseCodes.ReplacedByNewerConnection
    reason.message shouldBe "Replaced by a newer connection"
    registry.connectedPlayerCount() shouldBe 1

    registry.sendTo("alice", ServerMessage.LobbyState(nextRoundStartsAt = 1L, playersWaiting = 0))
    newSession.sent.tryReceive().isSuccess shouldBe true
    oldSession.sent.tryReceive().isSuccess shouldBe false
  }

  test("registering the very same connection instance again does not close it") {
    val registry = ConnectionRegistry()
    val session = FakeWebSocketServerSession()
    val connection = Connection(session)
    registry.register("alice", connection)

    registry.register("alice", connection)

    session.sent.tryReceive().isSuccess shouldBe false
    registry.connectedPlayerCount() shouldBe 1
  }

  test("unregister with a stale connection reference is a no-op once it was replaced") {
    val registry = ConnectionRegistry()
    val oldSession = FakeWebSocketServerSession()
    val newSession = FakeWebSocketServerSession()
    val oldConnection = Connection(oldSession)
    registry.register("alice", oldConnection)
    registry.register("alice", Connection(newSession))
    oldSession.sent.tryReceive() // drain the replacement's close frame

    registry.unregister("alice", oldConnection)

    registry.connectedPlayerCount() shouldBe 1
    registry.connectedPlayerIds() shouldContainExactlyInAnyOrder listOf("alice")
  }

  test("unregister with the current connection removes the player") {
    val registry = ConnectionRegistry()
    val connection = Connection(FakeWebSocketServerSession())
    registry.register("alice", connection)

    registry.unregister("alice", connection)

    registry.connectedPlayerCount() shouldBe 0
    registry.connectedPlayerIds().shouldBeEmpty()
  }

  test("connectedPlayerIds and connectedPlayerCount reflect every registered player") {
    val registry = ConnectionRegistry()
    registry.register("alice", Connection(FakeWebSocketServerSession()))
    registry.register("bob", Connection(FakeWebSocketServerSession()))

    registry.connectedPlayerCount() shouldBe 2
    registry.connectedPlayerIds() shouldContainExactlyInAnyOrder listOf("alice", "bob")
  }

  test("broadcast reaches every connected player with the exact same message") {
    val registry = ConnectionRegistry()
    val aliceSession = FakeWebSocketServerSession()
    val bobSession = FakeWebSocketServerSession()
    registry.register("alice", Connection(aliceSession))
    registry.register("bob", Connection(bobSession))
    val message = ServerMessage.LobbyState(nextRoundStartsAt = 42L, playersWaiting = 2)

    registry.broadcast(message)

    listOf(aliceSession, bobSession).forEach { session ->
      val frame = session.sent.tryReceive().getOrThrow()
      frame.shouldBeInstanceOf<Frame.Text>()
      PalavramentoJson.decodeFromString<ServerMessage>(frame.readText()) shouldBe message
    }
  }

  test("broadcast with no connected player does nothing and never throws") {
    val registry = ConnectionRegistry()

    registry.broadcast(ServerMessage.LobbyState(nextRoundStartsAt = 1L, playersWaiting = 0))

    registry.connectedPlayerCount() shouldBe 0
  }

  test("broadcastTo skips player ids that are not connected instead of failing") {
    val registry = ConnectionRegistry()
    val aliceSession = FakeWebSocketServerSession()
    registry.register("alice", Connection(aliceSession))
    val message = ServerMessage.LobbyState(nextRoundStartsAt = 5L, playersWaiting = 1)

    registry.broadcastTo(listOf("alice", "ghost"), message)

    val frame = aliceSession.sent.tryReceive().getOrThrow()
    frame.shouldBeInstanceOf<Frame.Text>()
    PalavramentoJson.decodeFromString<ServerMessage>(frame.readText()) shouldBe message
  }

  test("broadcastTo an empty collection sends nothing to anybody") {
    val registry = ConnectionRegistry()
    val aliceSession = FakeWebSocketServerSession()
    registry.register("alice", Connection(aliceSession))

    registry.broadcastTo(emptyList(), ServerMessage.LobbyState(nextRoundStartsAt = 5L, playersWaiting = 1))

    aliceSession.sent.tryReceive().isSuccess shouldBe false
  }

  test("a connection that dropped mid-send does not stop the broadcast reaching everyone else") {
    val registry = ConnectionRegistry()
    val deadSession = FakeWebSocketServerSession()
    deadSession.sent.close() // simulates the underlying socket having already gone away
    val aliveSession = FakeWebSocketServerSession()
    registry.register("dead", Connection(deadSession))
    registry.register("alive", Connection(aliveSession))
    val message = ServerMessage.LobbyState(nextRoundStartsAt = 1L, playersWaiting = 2)

    registry.broadcast(message)

    val delivered = aliveSession.sent.tryReceive().getOrNull().shouldBeInstanceOf<Frame.Text>()
    delivered.readText() shouldBe PalavramentoJson.encodeToString(ServerMessage.serializer(), message)
    // The dead one is gone from the registry: nothing left to fail on the next round's broadcast.
    registry.connectedPlayerIds() shouldBe setOf("alive")
  }

  test("sendTo a connection that dropped mid-send drops it instead of throwing at the caller") {
    val registry = ConnectionRegistry()
    val deadSession = FakeWebSocketServerSession()
    deadSession.sent.close()
    registry.register("dead", Connection(deadSession))

    registry.sendTo("dead", ServerMessage.LobbyState(nextRoundStartsAt = 1L, playersWaiting = 1))

    registry.connectedPlayerCount() shouldBe 0
  }
})
