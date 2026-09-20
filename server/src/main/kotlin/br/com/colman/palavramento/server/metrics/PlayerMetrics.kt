// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.metrics

import br.com.colman.palavramento.server.repository.PlayerRepository
import br.com.colman.palavramento.server.repository.RoundResultRepository
import br.com.colman.palavramento.server.round.GameClock
import br.com.colman.palavramento.server.ws.ConnectionRegistry
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

/** The windows the active-player gauge is reported over, each one a `window` label (ADR 0019). */
enum class ActivityWindow(val label: String, val span: Duration) {
  Day("24h", 24.hours),
  Week("7d", 7.days),
  Month("30d", 30.days),
}

/**
 * Player-facing gauges for Prometheus (ADR 0019): who is connected right now, how many distinct
 * players entered a round inside each [ActivityWindow], and how many accounts exist.
 *
 * Connections are counted straight from [ConnectionRegistry] whenever Prometheus scrapes, since that
 * is a number already in memory. The rest comes from the database, which a scrape must not query
 * directly: Prometheus scrapes every few seconds and a `COUNT(DISTINCT ...)` per scrape would make
 * the monitoring the heaviest client the server has. [start] refreshes those into plain counters
 * every [refreshInterval] instead, so a scrape only ever reads memory.
 */
class PlayerMetrics(
  registry: MeterRegistry,
  private val connections: ConnectionRegistry,
  private val roundResults: RoundResultRepository,
  private val players: PlayerRepository,
  private val clock: GameClock,
  private val refreshInterval: Duration,
) {

  /**
   * How far a player's own clock estimate is from this server's, in seconds, measured on every word
   * they submit: `serverNow - clientTimestamp`, so positive means the player is running behind.
   *
   * The countdown a player watches is driven by that estimate (dossier 5.3), so this is the number
   * that says whether a round can end while their timer still reads seconds left. Network delay
   * inflates it by one trip, which is tens of milliseconds and does not hide a skew worth seeing.
   */
  private val clockSkew: DistributionSummary = DistributionSummary.builder(ClockSkewSummary)
    .description("Seconds between a player's clock estimate and this server's, at submit time")
    .baseUnit("seconds")
    .publishPercentiles(0.5, 0.95, 0.99)
    .register(registry)

  private val activePlayers = ActivityWindow.entries.associateWith { AtomicLong() }
  private val guestPlayers = AtomicLong()
  private val registeredPlayers = AtomicLong()

  init {
    Gauge.builder(ConnectedGauge) { connections.connectedPlayerCount().toDouble() }
      .description("Players with an open multiplayer socket right now")
      .register(registry)

    activePlayers.forEach { (window, value) ->
      Gauge.builder(ActiveGauge) { value.get().toDouble() }
        .description("Distinct players who entered a round inside the window")
        .tag(WindowTag, window.label)
        .register(registry)
    }

    registerTotal(registry, KindGuest, guestPlayers)
    registerTotal(registry, KindRegistered, registeredPlayers)
  }

  /** Records how far [clientTimestampMs] fell from this server's clock. See [clockSkew]. */
  fun recordClientClockSkew(clientTimestampMs: Long) {
    clockSkew.record((clock.now().toEpochMilli() - clientTimestampMs) / MillisPerSecond)
  }

  /** Refreshes the database-backed gauges forever, until [scope] is cancelled. */
  fun start(scope: CoroutineScope): Job = scope.launch {
    while (isActive) {
      refresh()
      delay(refreshInterval)
    }
  }

  /** Reads the current numbers from the database into the gauges. Called by [start], and by tests. */
  suspend fun refresh() {
    val now = clock.now()
    ActivityWindow.entries.forEach { window ->
      val since = now.minus(window.span.toJavaDuration())
      activePlayers.getValue(window).set(roundResults.countDistinctPlayersSince(since))
    }
    val byKind = players.countByKind()
    guestPlayers.set(byKind[true] ?: 0)
    registeredPlayers.set(byKind[false] ?: 0)
  }

  private fun registerTotal(registry: MeterRegistry, kind: String, value: AtomicLong) {
    Gauge.builder(TotalGauge) { value.get().toDouble() }
      .description("Player accounts on this server")
      .tag(KindTag, kind)
      .register(registry)
  }

  private companion object {
    const val ClockSkewSummary = "palavramento.client.clock.skew"
    const val MillisPerSecond = 1000.0
    const val ConnectedGauge = "palavramento.players.connected"
    const val ActiveGauge = "palavramento.players.active"

    // Not "...players.total": Prometheus reserves the _total suffix for counters, and Micrometer
    // strips it from a gauge's name, which would leave a bare palavramento_players on the wire.
    const val TotalGauge = "palavramento.players.accounts"
    const val WindowTag = "window"
    const val KindTag = "kind"
    const val KindGuest = "guest"
    const val KindRegistered = "registered"
  }
}
