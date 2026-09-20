// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.ws

import br.com.colman.palavramento.domain.protocol.ServerMessage
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private val logger = LoggerFactory.getLogger("ConnectionRegistry")

/**
 * Tracks the one live connection per authenticated player (dossier phase 3 task: "if the same
 * player opens a second connection, the newest one wins"). Not per-round state: it outlives any
 * single round, since a player can be connected across the lobby/round/results cycle.
 */
class ConnectionRegistry {
  private val byPlayer = ConcurrentHashMap<String, Connection>()

  /** Registers [connection] as [playerId]'s current one, closing whatever connection it replaces. */
  suspend fun register(playerId: String, connection: Connection) {
    val previous = byPlayer.put(playerId, connection)
    if (previous != null && previous !== connection) {
      previous.close(CloseCodes.ReplacedByNewerConnection, "Replaced by a newer connection")
    }
  }

  /** Removes [connection] only if it is still [playerId]'s current one (an old, already-replaced one is a no-op). */
  fun unregister(playerId: String, connection: Connection) {
    byPlayer.remove(playerId, connection)
  }

  /**
   * Closes and drops [playerId]'s current connection, if it has one: e.g. its account was just
   * deleted (ADR 0020), so the access token backing this socket no longer resolves to anyone. A
   * no-op when [playerId] has no open connection, which is the common case: most players are not
   * mid-round when they delete their account.
   */
  suspend fun disconnect(playerId: String, code: Short, reason: String) {
    val connection = byPlayer.remove(playerId) ?: return
    connection.close(code, reason)
  }

  fun connectedPlayerCount(): Int = byPlayer.size

  fun connectedPlayerIds(): Set<String> = byPlayer.keys.toSet()

  suspend fun sendTo(playerId: String, message: ServerMessage) {
    val connection = byPlayer[playerId] ?: return
    sendIsolated(playerId, connection, message)
  }

  suspend fun broadcast(message: ServerMessage) {
    byPlayer.forEach { (playerId, connection) -> sendIsolated(playerId, connection, message) }
  }

  suspend fun broadcastTo(playerIds: Collection<String>, message: ServerMessage) {
    playerIds.forEach { sendTo(it, message) }
  }

  /**
   * Sends and keeps a socket that died in the meantime to itself. A player can vanish (phone asleep,
   * tunnel dropped) between the frame that proved the connection alive and this one, and the write
   * then throws. Letting that through would end the whole fan-out, so the players after the dead one
   * in a RoundEnd or Leaderboard broadcast would never hear how the round finished. The connection is
   * dropped from the registry instead: its own reader coroutine is on the way out anyway.
   */
  @Suppress("TooGenericExceptionCaught") // Whatever the transport throws, the answer is the same: drop it.
  private suspend fun sendIsolated(playerId: String, connection: Connection, message: ServerMessage) {
    try {
      connection.send(message)
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (failure: Exception) {
      unregister(playerId, connection)
      logger.info("Dropped {}'s connection: it failed while being sent a message ({})", playerId, failure.message)
    }
  }
}
