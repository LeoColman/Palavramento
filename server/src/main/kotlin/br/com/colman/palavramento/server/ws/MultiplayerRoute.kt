// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.ws

import br.com.colman.palavramento.domain.protocol.ClientMessage
import br.com.colman.palavramento.domain.protocol.PalavramentoJson
import br.com.colman.palavramento.domain.protocol.ServerMessage
import br.com.colman.palavramento.server.auth.JwtService
import br.com.colman.palavramento.server.config.ServerConfig
import br.com.colman.palavramento.server.metrics.PlayerMetrics
import br.com.colman.palavramento.server.round.GameClock
import br.com.colman.palavramento.server.round.JoinResult
import br.com.colman.palavramento.server.round.RoomScheduler
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.decodeFromString
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("MultiplayerRoute")

/** Mutable per-connection state: who this socket is, once `JoinRoom` succeeds, and its own rate limiter. */
private class MultiplayerSession(val connection: Connection, val rateLimiter: RateLimiter) {
  var playerId: String? = null
}

/**
 * What every handler below needs, bundled: the socket is one long conversation with the same four
 * collaborators, and threading them one by one made each handler grow a parameter list nobody can
 * read.
 */
private class RouteDependencies(
  val scheduler: RoomScheduler,
  val jwtService: JwtService,
  val clock: GameClock,
  val metrics: PlayerMetrics,
)

/**
 * `/ws/multiplayer` (dossier §5): anonymous until a valid `JoinRoom.sessionToken`, then routes
 * every other message to [scheduler]. One [RateLimiter] per connection (dossier phase 3 task: "10
 * submissions/s per connection, excess silently dropped and logged").
 */
fun Route.multiplayerRoute(
  scheduler: RoomScheduler,
  jwtService: JwtService,
  config: ServerConfig,
  clock: GameClock,
  metrics: PlayerMetrics,
) {
  val dependencies = RouteDependencies(scheduler, jwtService, clock, metrics)
  webSocket("/ws/multiplayer") {
    val session = MultiplayerSession(Connection(this), RateLimiter(config.submitRateLimitPerSecond, clock))
    try {
      for (frame in incoming) {
        val message = frame.decodeClientMessage() ?: continue
        val keepGoing = handleMessage(dependencies, session, message)
        if (!keepGoing) return@webSocket
      }
    } finally {
      session.playerId?.let { scheduler.connectionRegistry.unregister(it, session.connection) }
    }
  }
}

private fun Frame.decodeClientMessage(): ClientMessage? {
  if (this !is Frame.Text) return null
  return runCatching { PalavramentoJson.decodeFromString<ClientMessage>(readText()) }.getOrNull()
}

/** Dispatches one [message]; returns false when the caller should stop reading (the socket closed). */
private suspend fun handleMessage(
  dependencies: RouteDependencies,
  session: MultiplayerSession,
  message: ClientMessage,
): Boolean {
  val playerId = session.playerId
  if (playerId == null && message !is ClientMessage.ClockSync && message !is ClientMessage.JoinRoom) {
    session.connection.close(CloseCodes.InvalidToken, "JoinRoom with a valid sessionToken is required first")
    return false
  }

  return when (message) {
    is ClientMessage.ClockSync -> {
      val serverTime = dependencies.clock.now().toEpochMilli()
      session.connection.send(ServerMessage.ClockSyncResponse(message.clientSentAt, serverTime))
      true
    }
    is ClientMessage.JoinRoom -> handleJoinRoom(dependencies, session, message)
    is ClientMessage.SubmitWord -> {
      handleSubmitWord(dependencies, session, checkNotNull(playerId) { "guarded above" }, message)
      true
    }
    is ClientMessage.LeaveRoom -> {
      handleLeaveRoom(dependencies.scheduler, session, checkNotNull(playerId) { "guarded above" })
      false
    }
  }
}

private suspend fun handleJoinRoom(
  dependencies: RouteDependencies,
  session: MultiplayerSession,
  message: ClientMessage.JoinRoom,
): Boolean {
  val scheduler = dependencies.scheduler
  val verified = message.sessionToken?.let { dependencies.jwtService.verifyAccessToken(it) }
  if (verified == null) {
    session.connection.close(CloseCodes.InvalidToken, "Invalid or missing session token")
    return false
  }
  session.playerId = verified.playerId
  scheduler.connectionRegistry.register(verified.playerId, session.connection)
  val result = scheduler.join(verified.playerId)
  session.connection.send(if (result is JoinResult.Started) result.message else (result as JoinResult.Waiting).message)
  return true
}

private suspend fun handleSubmitWord(
  dependencies: RouteDependencies,
  session: MultiplayerSession,
  playerId: String,
  message: ClientMessage.SubmitWord,
) {
  if (session.rateLimiter.tryAcquire()) {
    // Before anything else: this is the only place the server ever sees a player's own clock
    // (ADR 0019's palavramento_client_clock_skew).
    dependencies.metrics.recordClientClockSkew(message.clientTimestamp)
    val response = dependencies.scheduler.submitWord(playerId, message.roundId, message.path)
    if (response != null) session.connection.send(response)
  } else {
    logger.info("Rate limit exceeded for player {}", playerId)
  }
}

private suspend fun handleLeaveRoom(scheduler: RoomScheduler, session: MultiplayerSession, playerId: String) {
  scheduler.connectionRegistry.unregister(playerId, session.connection)
  session.connection.close(CloseReason.Codes.NORMAL.code, "Left the room")
}
