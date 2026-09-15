// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.network

import br.com.colman.palavramento.domain.protocol.ClientMessage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private fun submission(roundId: String, path: List<Int>) = ClientMessage.SubmitWord(roundId, path, clientTimestamp = 0)

class PendingSubmissionQueueTest : FunSpec({

  test("A fresh queue has nothing to drain") {
    val queue = PendingSubmissionQueue()
    queue.size shouldBe 0
    queue.drain("round-1") shouldBe emptyList()
  }

  test("enqueue then drain for the same round returns them in insertion order and empties the queue") {
    val queue = PendingSubmissionQueue()
    val first = submission("round-1", listOf(0, 1, 2))
    val second = submission("round-1", listOf(3, 4, 5))

    queue.enqueue(first)
    queue.enqueue(second)
    queue.size shouldBe 2

    queue.drain("round-1") shouldBe listOf(first, second)
    queue.size shouldBe 0
  }

  test("drain for a different round drops the queued submissions instead of returning them") {
    val queue = PendingSubmissionQueue()
    queue.enqueue(submission("round-1", listOf(0, 1, 2)))

    queue.drain("round-2") shouldBe emptyList()
    // The stale entry is gone, not left behind for a later round-1 drain to pick up.
    queue.drain("round-1") shouldBe emptyList()
  }

  test("drain only returns submissions matching the current round, dropping the rest") {
    val queue = PendingSubmissionQueue()
    val forCurrentRound = submission("round-2", listOf(6, 7))
    queue.enqueue(submission("round-1", listOf(0, 1, 2)))
    queue.enqueue(forCurrentRound)

    queue.drain("round-2") shouldBe listOf(forCurrentRound)
  }

  test("clear discards every queued submission") {
    val queue = PendingSubmissionQueue()
    queue.enqueue(submission("round-1", listOf(0, 1, 2)))

    queue.clear()

    queue.size shouldBe 0
    queue.drain("round-1") shouldBe emptyList()
  }
})
