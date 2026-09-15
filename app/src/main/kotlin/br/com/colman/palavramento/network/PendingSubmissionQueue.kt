// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.network

import br.com.colman.palavramento.domain.protocol.ClientMessage

/**
 * Buffers `SubmitWord` messages made while disconnected (dossier 5.3, task brief 5: "submissoes
 * feitas enquanto desconectado sao enfileiradas e enviadas apos o handshake de reconexao, se a
 * mesma rodada ainda estiver rodando"). [MultiplayerSession] enqueues instead of sending straight to
 * a dead transport, then [drain]s once the reconnect handshake and `JoinRoom` finish and a fresh
 * `RoundStart` names the round that is actually running now.
 *
 * Kept free of coroutines/Android so it is unit-testable on the JVM in isolation from
 * [MultiplayerSession]'s network orchestration (task brief 8).
 */
class PendingSubmissionQueue {
  private val pending = mutableListOf<ClientMessage.SubmitWord>()

  /** Number of submissions currently queued. */
  val size: Int get() = pending.size

  /** Queues [submission] for later delivery. */
  fun enqueue(submission: ClientMessage.SubmitWord) {
    pending += submission
  }

  /**
   * Removes every queued submission and returns the ones whose `roundId` matches
   * [currentRoundId], in the order they were made. Anything queued for a different round is
   * dropped (not returned): the server has nothing left to validate it against once its round has
   * ended, and a queued submission is only ever tagged with the round that was running when it was
   * made, so it can never match a round that starts later.
   */
  fun drain(currentRoundId: String): List<ClientMessage.SubmitWord> {
    val matching = pending.filter { it.roundId == currentRoundId }
    pending.clear()
    return matching
  }

  /** Discards every queued submission, e.g. when the player explicitly leaves the room. */
  fun clear() {
    pending.clear()
  }
}
