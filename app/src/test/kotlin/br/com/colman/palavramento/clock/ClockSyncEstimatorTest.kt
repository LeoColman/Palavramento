// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.clock

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlin.math.abs

class ClockSyncEstimatorTest : FunSpec({

  test("A single sample's offset is server time minus its round-trip midpoint") {
    val sample =
      ClockSyncSample(clientSentAtElapsedMs = 1_000, serverTimeMs = 50_000, clientReceivedAtElapsedMs = 1_200)
    // midpoint = 1000 + (200 / 2) = 1100; offset = 50000 - 1100
    sample.serverTimeMs shouldBe 50_000
    sample.roundTripMs shouldBe 200
    sample.midpointElapsedMs shouldBe 1_100
    sample.offsetMs shouldBe 48_900
  }

  test("A response cannot be recorded as arriving before it was sent") {
    shouldThrow<IllegalArgumentException> {
      ClockSyncSample(clientSentAtElapsedMs = 1_000, serverTimeMs = 0, clientReceivedAtElapsedMs = 999)
    }
  }

  test("With one sample, that sample's offset is the estimator's offset") {
    val estimator = ClockSyncEstimator()
    val sample = ClockSyncSample(0, 100_000, 40)
    estimator.record(sample)
    estimator.offsetMs shouldBe sample.offsetMs
    estimator.bestSample shouldBe sample
  }

  test("Before any sample, the estimator has no offset") {
    ClockSyncEstimator().offsetMs shouldBe null
  }

  test("The estimator always keeps the lowest round-trip sample, regardless of arrival order") {
    checkAll(
      Arb.long(0L..1_000_000L),
      Arb.long(0L..1_000_000L),
      Arb.long(0L..1_000_000L)
    ) { sentAt, roundTripA, roundTripB ->
      // Staleness has its own tests below; here the rule under test is the round trip one, and the
      // generated round trips reach a thousand seconds, which would age the first sample out.
      val estimator = ClockSyncEstimator(staleAfterMs = Long.MAX_VALUE)
      val sampleA = ClockSyncSample(sentAt, 0, sentAt + roundTripA)
      val sampleB = ClockSyncSample(sentAt, 0, sentAt + roundTripB)

      estimator.record(sampleA)
      estimator.record(sampleB)

      val expected = if (sampleA.roundTripMs <= sampleB.roundTripMs) sampleA else sampleB
      estimator.bestSample shouldBe expected
    }
  }

  test("Recording a worse sample after a better one keeps the better one") {
    val better = Arb.long(0L..100L)
    checkAll(better) { bestRoundTrip ->
      val estimator = ClockSyncEstimator()
      val best = ClockSyncSample(0, 0, bestRoundTrip)
      estimator.record(best)
      estimator.record(ClockSyncSample(0, 0, bestRoundTrip + 1))
      estimator.bestSample shouldBe best
    }
  }

  test("a worse sample still wins once the kept one went stale") {
    val estimator = ClockSyncEstimator(staleAfterMs = 45_000)
    // Connect time, mobile radio waking up: a fat round trip, so a biased offset.
    estimator.record(ClockSyncSample(clientSentAtElapsedMs = 0, serverTimeMs = 100_000, clientReceivedAtElapsedMs = 40))

    // A minute later, with the radio awake: a worse round trip on paper, but the honest one now.
    estimator.record(
      ClockSyncSample(clientSentAtElapsedMs = 60_000, serverTimeMs = 160_100, clientReceivedAtElapsedMs = 60_100),
    )

    estimator.bestSample?.clientSentAtElapsedMs shouldBe 60_000
  }

  test("a worse sample loses while the kept one is still fresh") {
    val estimator = ClockSyncEstimator(staleAfterMs = 45_000)
    estimator.record(ClockSyncSample(clientSentAtElapsedMs = 0, serverTimeMs = 100_000, clientReceivedAtElapsedMs = 40))

    estimator.record(
      ClockSyncSample(clientSentAtElapsedMs = 10_000, serverTimeMs = 110_100, clientReceivedAtElapsedMs = 10_200),
    )

    estimator.bestSample?.clientSentAtElapsedMs shouldBe 0
  }

  test("a five second round trip never takes over, however stale the kept sample went") {
    val estimator = ClockSyncEstimator(staleAfterMs = 45_000, maxRoundTripMs = 1_000)
    // An honest sample: 80 ms round trip.
    estimator.record(ClockSyncSample(clientSentAtElapsedMs = 0, serverTimeMs = 100_040, clientReceivedAtElapsedMs = 80))

    // Two minutes later, far past stale, the network hiccups: five seconds, all of it coming back.
    // This is the sample that ended a player's round with two seconds still on their countdown.
    estimator.record(
      ClockSyncSample(clientSentAtElapsedMs = 120_000, serverTimeMs = 220_000, clientReceivedAtElapsedMs = 125_000),
    )

    estimator.bestSample?.clientSentAtElapsedMs shouldBe 0
  }

  test("the very first sample is kept whatever it measured, so there is a clock at all") {
    val estimator = ClockSyncEstimator(maxRoundTripMs = 1_000)
    val slow = ClockSyncSample(clientSentAtElapsedMs = 0, serverTimeMs = 50_000, clientReceivedAtElapsedMs = 9_000)

    estimator.record(slow)

    estimator.bestSample shouldBe slow
  }

  test("anything inside the ceiling replaces a slow first sample at once, without waiting for it to go stale") {
    val estimator = ClockSyncEstimator(staleAfterMs = 45_000, maxRoundTripMs = 1_000)
    estimator.record(
      ClockSyncSample(clientSentAtElapsedMs = 0, serverTimeMs = 50_000, clientReceivedAtElapsedMs = 9_000),
    )
    val honest =
      ClockSyncSample(clientSentAtElapsedMs = 10_000, serverTimeMs = 60_000, clientReceivedAtElapsedMs = 10_100)

    estimator.record(honest)

    estimator.bestSample shouldBe honest
  }

  test("with nothing inside the ceiling yet, the least bad sample is still the one kept") {
    val estimator = ClockSyncEstimator(staleAfterMs = 45_000, maxRoundTripMs = 1_000)
    estimator.record(
      ClockSyncSample(clientSentAtElapsedMs = 0, serverTimeMs = 50_000, clientReceivedAtElapsedMs = 9_000),
    )
    val lessBad =
      ClockSyncSample(clientSentAtElapsedMs = 10_000, serverTimeMs = 60_000, clientReceivedAtElapsedMs = 13_000)

    estimator.record(lessBad)

    estimator.bestSample shouldBe lessBad
  }

  test("a sample measured at exactly the ceiling is still trusted enough to refresh a stale one") {
    val estimator = ClockSyncEstimator(staleAfterMs = 45_000, maxRoundTripMs = 1_000)
    estimator.record(
      ClockSyncSample(clientSentAtElapsedMs = 0, serverTimeMs = 100_000, clientReceivedAtElapsedMs = 500),
    )
    // A whole second, the ceiling exactly: inside it, so it may take over a sample gone stale.
    val atTheCeiling =
      ClockSyncSample(clientSentAtElapsedMs = 59_000, serverTimeMs = 160_000, clientReceivedAtElapsedMs = 60_000)

    estimator.record(atTheCeiling)

    estimator.bestSample shouldBe atTheCeiling
  }

  test("between two samples both exactly at the ceiling, the one already kept stays") {
    val estimator = ClockSyncEstimator(staleAfterMs = 45_000, maxRoundTripMs = 1_000)
    val first = ClockSyncSample(clientSentAtElapsedMs = 0, serverTimeMs = 100_000, clientReceivedAtElapsedMs = 1_000)
    estimator.record(first)

    estimator.record(
      ClockSyncSample(clientSentAtElapsedMs = 2_000, serverTimeMs = 102_000, clientReceivedAtElapsedMs = 3_000),
    )

    estimator.bestSample shouldBe first
  }

  test("with nothing inside the ceiling, an equally bad sample does not displace the kept one") {
    val estimator = ClockSyncEstimator(staleAfterMs = 45_000, maxRoundTripMs = 1_000)
    val first = ClockSyncSample(clientSentAtElapsedMs = 0, serverTimeMs = 100_000, clientReceivedAtElapsedMs = 5_000)
    estimator.record(first)

    estimator.record(
      ClockSyncSample(clientSentAtElapsedMs = 6_000, serverTimeMs = 106_000, clientReceivedAtElapsedMs = 11_000),
    )

    estimator.bestSample shouldBe first
  }

  test("an equally good sample does not displace the kept one either") {
    val estimator = ClockSyncEstimator(staleAfterMs = 45_000, maxRoundTripMs = 1_000)
    val first = ClockSyncSample(clientSentAtElapsedMs = 0, serverTimeMs = 100_000, clientReceivedAtElapsedMs = 100)
    estimator.record(first)

    estimator.record(
      ClockSyncSample(clientSentAtElapsedMs = 1_000, serverTimeMs = 101_000, clientReceivedAtElapsedMs = 1_100),
    )

    estimator.bestSample shouldBe first
  }

  test("a sample arriving exactly staleAfterMs later has not made the kept one stale yet") {
    val estimator = ClockSyncEstimator(staleAfterMs = 45_000, maxRoundTripMs = 1_000)
    // Received at 10_000, not at zero, so measuring the gap by adding instead of subtracting
    // would not land on the same number.
    val first =
      ClockSyncSample(clientSentAtElapsedMs = 9_900, serverTimeMs = 100_000, clientReceivedAtElapsedMs = 10_000)
    estimator.record(first)

    // Exactly 45_000 later, and a worse round trip: staleness is strictly greater than, so it loses.
    estimator.record(
      ClockSyncSample(clientSentAtElapsedMs = 54_800, serverTimeMs = 145_000, clientReceivedAtElapsedMs = 55_000),
    )

    estimator.bestSample shouldBe first
  }

  test("once a sample inside the ceiling arrives, the offset is never off by more than half the ceiling") {
    val ceiling = 1_000L
    checkAll(
      Arb.long(-5_000L..5_000L),
      Arb.long(0L..500L),
      Arb.long(0L..500L),
      Arb.long(1_001L..60_000L),
    ) { trueOffset, uplink, downlink, hiccupMs ->
      val estimator = ClockSyncEstimator(staleAfterMs = 45_000, maxRoundTripMs = ceiling)
      // A sample whose delay all went one way, then an honest one, then one that all came back.
      estimator.record(sampleFor(trueOffset, sentAt = 0, uplinkMs = hiccupMs, downlinkMs = 0))
      estimator.record(sampleFor(trueOffset, sentAt = 100_000, uplinkMs = uplink, downlinkMs = downlink))
      estimator.record(sampleFor(trueOffset, sentAt = 200_000, uplinkMs = 0, downlinkMs = hiccupMs))

      abs(estimator.offsetMs!! - trueOffset) shouldBeLessThanOrEqual ceiling / 2
    }
  }
})

/**
 * A sample of a server whose clock really is [trueOffsetMs] ahead of the client's elapsed time,
 * answered after [uplinkMs] and carried back over [downlinkMs]. The estimator cannot see those two
 * legs apart, which is the whole reason an offset can be wrong at all.
 */
private fun sampleFor(trueOffsetMs: Long, sentAt: Long, uplinkMs: Long, downlinkMs: Long) = ClockSyncSample(
  clientSentAtElapsedMs = sentAt,
  serverTimeMs = sentAt + uplinkMs + trueOffsetMs,
  clientReceivedAtElapsedMs = sentAt + uplinkMs + downlinkMs,
)
