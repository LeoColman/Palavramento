// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.clock

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

class ClockSyncEstimatorTest : FunSpec({

  test("A single sample's offset is server time minus its round-trip midpoint") {
    val sample =
      ClockSyncSample(clientSentAtElapsedMs = 1_000, serverTimeMs = 50_000, clientReceivedAtElapsedMs = 1_200)
    // midpoint = 1000 + (200 / 2) = 1100; offset = 50000 - 1100
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
      val estimator = ClockSyncEstimator()
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
})
