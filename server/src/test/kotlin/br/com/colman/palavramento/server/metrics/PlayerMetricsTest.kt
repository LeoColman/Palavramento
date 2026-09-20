// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.metrics

import br.com.colman.palavramento.server.repository.PlayerRepository
import br.com.colman.palavramento.server.repository.RoundRepository
import br.com.colman.palavramento.server.repository.RoundResultRepository
import br.com.colman.palavramento.server.repository.RoundResultRow
import br.com.colman.palavramento.server.round.MutableGameClock
import br.com.colman.palavramento.server.testsupport.insertFakeFinishedRound
import br.com.colman.palavramento.server.testsupport.insertGuest
import br.com.colman.palavramento.server.testsupport.testDatabase
import br.com.colman.palavramento.server.testsupport.testRoomId
import br.com.colman.palavramento.server.ws.Connection
import br.com.colman.palavramento.server.ws.ConnectionRegistry
import br.com.colman.palavramento.server.ws.FakeWebSocketServerSession
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

private val Now: Instant = Instant.parse("2026-09-19T12:00:00Z")
private const val DayInSeconds = 24L * 60 * 60

/** [playerId] takes part in a round of [roomId], having joined it at [enteredAt]. */
private suspend fun enterRound(database: Database, roomId: String, playerId: String, enteredAt: Instant) {
  val roundId = RoundRepository(database).insertFakeFinishedRound(roomId)
  suspendTransaction(database) {
    RoundResultRepository(database).insert(
      this,
      RoundResultRow(roundId, playerId, score = 1, words = 1, rank = 1, xp = 1, enteredAt = enteredAt),
    )
  }
}

private fun PrometheusMeterRegistry.activePlayers(window: ActivityWindow): Double =
  get("palavramento.players.active").tag("window", window.label).gauge().value()

private fun PrometheusMeterRegistry.playersOfKind(kind: String): Double =
  get("palavramento.players.accounts").tag("kind", kind).gauge().value()

/**
 * The gauges Prometheus scrapes (ADR 0019). Every spec here shares one database with the rest of the
 * suite, so the active-player specs use players they created themselves and the account totals are
 * asserted as a change, never as an absolute number.
 */
class PlayerMetricsTest : FunSpec({
  val database = testDatabase()

  test("the connected gauge reads the connection registry live, with no refresh in between") {
    runTest {
      val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
      val connections = ConnectionRegistry()
      PlayerMetrics(
        registry,
        connections,
        RoundResultRepository(database),
        PlayerRepository(database),
        MutableGameClock(Now),
        refreshInterval = 1.minutes,
      )

      registry.get("palavramento.players.connected").gauge().value() shouldBe 0.0

      connections.register("p1", Connection(FakeWebSocketServerSession()))

      registry.get("palavramento.players.connected").gauge().value() shouldBe 1.0
    }
  }

  test("a player counts once per window, however many rounds they played in it") {
    runTest {
      val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
      val metrics = PlayerMetrics(
        registry,
        ConnectionRegistry(),
        RoundResultRepository(database),
        PlayerRepository(database),
        MutableGameClock(Now),
        refreshInterval = 1.minutes,
      )
      val roomId = testRoomId()
      val player = PlayerRepository(database).insertGuest()
      val before = ActivityWindow.entries.associateWith { window ->
        metrics.refresh()
        registry.activePlayers(window)
      }
      enterRound(database, roomId, player.id, Now.minusSeconds(60))
      enterRound(database, roomId, player.id, Now.minusSeconds(120))

      metrics.refresh()

      ActivityWindow.entries.forEach { window ->
        registry.activePlayers(window) shouldBe before.getValue(window) + 1.0
      }
    }
  }

  test("a window counts its own edge, and nothing older than it") {
    runTest {
      val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
      val metrics = PlayerMetrics(
        registry,
        ConnectionRegistry(),
        RoundResultRepository(database),
        PlayerRepository(database),
        MutableGameClock(Now),
        refreshInterval = 1.minutes,
      )
      val roomId = testRoomId()
      val players = PlayerRepository(database)
      val onTheEdge = players.insertGuest()
      val justOutside = players.insertGuest()
      metrics.refresh()
      val dayBefore = registry.activePlayers(ActivityWindow.Day)
      val weekBefore = registry.activePlayers(ActivityWindow.Week)
      // Exactly 24 hours ago is still active today; one second before that is not.
      enterRound(database, roomId, onTheEdge.id, Now.minusSeconds(DayInSeconds))
      enterRound(database, roomId, justOutside.id, Now.minusSeconds(DayInSeconds + 1))

      metrics.refresh()

      registry.activePlayers(ActivityWindow.Day) shouldBe dayBefore + 1.0
      registry.activePlayers(ActivityWindow.Week) shouldBe weekBefore + 2.0
    }
  }

  test("a new guest moves the guest total and leaves the registered total alone") {
    runTest {
      val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
      val metrics = PlayerMetrics(
        registry,
        ConnectionRegistry(),
        RoundResultRepository(database),
        PlayerRepository(database),
        MutableGameClock(Now),
        refreshInterval = 1.minutes,
      )
      metrics.refresh()
      val guestsBefore = registry.playersOfKind("guest")
      val registeredBefore = registry.playersOfKind("registered")

      PlayerRepository(database).insertGuest()
      metrics.refresh()

      registry.playersOfKind("guest") shouldBe guestsBefore + 1.0
      registry.playersOfKind("registered") shouldBe registeredBefore
    }
  }
})
