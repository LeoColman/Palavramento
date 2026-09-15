// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.ws

import br.com.colman.palavramento.domain.protocol.ServerMessage
import java.util.concurrent.ConcurrentHashMap

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

  fun connectedPlayerCount(): Int = byPlayer.size

  fun connectedPlayerIds(): Set<String> = byPlayer.keys.toSet()

  suspend fun sendTo(playerId: String, message: ServerMessage) {
    byPlayer[playerId]?.send(message)
  }

  suspend fun broadcast(message: ServerMessage) {
    byPlayer.values.forEach { it.send(message) }
  }

  suspend fun broadcastTo(playerIds: Collection<String>, message: ServerMessage) {
    playerIds.forEach { sendTo(it, message) }
  }
}
