// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.network

import br.com.colman.palavramento.clock.ServerClock
import br.com.colman.palavramento.domain.protocol.ClientMessage
import br.com.colman.palavramento.domain.protocol.ServerMessage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

/**
 * The clock the countdown reads (dossier 5.3). The case that matters is the one that sent a player
 * to the results screen with seconds still on their timer: the samples taken at connect time were
 * biased, and nothing corrected them for the rest of the round.
 */
class ClockSyncCoordinatorTest : FunSpec({

  test("the handshake publishes an offset from the server's answer") {
    runTest {
      val transport = FakeMultiplayerTransport()
      val clock = MutableStateFlow<ServerClock?>(null)
      var elapsed = 1_000L
      val coordinator = ClockSyncCoordinator(
        transport,
        { elapsed },
        ClockSyncSettings(sampleCount = 1),
        clockFlow = clock,
      )
      transport.connect()
      // The answer has to be queued before the handshake reads it, so it echoes the sentAt it sees.
      transport.push(ServerMessage.ClockSyncResponse(clientSentAt = 1_000, serverTime = 500_000))

      coordinator.handshake()

      transport.sent shouldBe listOf(ClientMessage.ClockSync(1_000))
      // Round trip of zero in this fake, so the midpoint is the send instant: 500000 - 1000.
      clock.value.shouldNotBeNull().nowMs() shouldBe 500_000
    }
  }

  test("a later, tighter sample corrects a clock the handshake got wrong") {
    runTest {
      val transport = FakeMultiplayerTransport()
      val clock = MutableStateFlow<ServerClock?>(null)
      // Readings in order: the handshake sends at 0 and only gets the answer 4 s later, which is
      // what a mobile radio waking up looks like. Anything after that reads the last value.
      val readings = ArrayDeque(listOf(0L, 4_000L))
      var last = 4_000L
      val elapsed = { readings.removeFirstOrNull()?.also { last = it } ?: last }
      val coordinator = ClockSyncCoordinator(
        transport,
        elapsed,
        ClockSyncSettings(sampleCount = 1),
        clockFlow = clock,
      )
      transport.connect()
      transport.push(ServerMessage.ClockSyncResponse(clientSentAt = 0, serverTime = 100_000))

      coordinator.handshake()
      val afterHandshake = clock.value.shouldNotBeNull().nowMs()

      // Mid-round, radio awake: sent at 20 s, answered 20 ms later, server says 120 s.
      last = 20_020
      coordinator.record(ServerMessage.ClockSyncResponse(clientSentAt = 20_000, serverTime = 120_000))

      // The biased handshake had the client believing the server was 2 s behind where it really is:
      // 102 s against the 104 s that had actually passed on the server by then.
      afterHandshake shouldBe 102_000
      clock.value.shouldNotBeNull().nowMs() shouldBe 120_010
    }
  }

  test("keepSyncing asks for the time once per interval") {
    runTest {
      val transport = FakeMultiplayerTransport()
      val waits = mutableListOf<Long>()
      var elapsed = 0L
      val settings = ClockSyncSettings(
        sampleCount = 1,
        resyncIntervalMs = 5_000,
        wait = { waited ->
          waits += waited
          elapsed += waited
          if (waits.size == 3) error("enough")
        },
      )
      val coordinator = ClockSyncCoordinator(transport, { elapsed }, settings, MutableStateFlow(null))
      transport.connect()

      runCatching { coordinator.keepSyncing() }

      waits shouldBe listOf(5_000L, 5_000L, 5_000L)
      transport.sent shouldHaveSize 2
      transport.sent.first() shouldBe ClientMessage.ClockSync(5_000)
    }
  }
})
