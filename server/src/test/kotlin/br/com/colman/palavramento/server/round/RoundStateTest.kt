// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.round

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.lexicon.InMemoryLexicon
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.submission.RejectionReason
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant

// L O A R    "LIMO" is traced 0-5-4-1 and "MIL" is 4-5-0: both only use adjacent, distinct tiles.
// M I C T
// P V R I
// E O S M
private fun referenceBoard() = Board(
  4,
  listOf(
    Tile("L", 10), Tile("O", 2), Tile("A", 1), Tile("R", 2),
    Tile("M", 3), Tile("I", 2), Tile("C", 4), Tile("T", 4),
    Tile("P", 5), Tile("V", 6), Tile("R", 2), Tile("I", 2),
    Tile("E", 1), Tile("O", 2), Tile("S", 1), Tile("M", 3),
  ),
)

private val StartsAt = Instant.parse("2026-01-01T00:00:00Z")

private fun record(id: String = "round-1") = RoundRecord(
  id = id,
  roomId = GlobalRoomId,
  seed = 42,
  board = referenceBoard(),
  mutator = Mutator.NoMutator,
  themeTitle = "Sem mutador",
  themeSubtitle = "Palavras comuns",
  commonMin = 3,
  maxScore = 100,
  maxWords = 20,
  startsAt = StartsAt,
  endsAt = StartsAt.plusSeconds(120),
  status = RoundStatus.Active,
)

private fun lexicon() = InMemoryLexicon.of("limo", "mil")

/**
 * [RoundState] needs no database of its own: persistence is the `onAccepted` callback, so this spec
 * collects it in a list and checks exactly what the round hands over. The Testcontainers specs cover
 * the same paths end to end; these pin the per-player bookkeeping the wire protocol reads back.
 */
class RoundStateTest : FunSpec({
  test("record reads through to the generated round's own record") {
    val generated = GeneratedRound(record("round-7"), emptyList())
    val state = RoundState(generated) { }

    state.record.id shouldBe "round-7"
    state.record.roomId shouldBe GlobalRoomId
    state.generated shouldBe generated
  }

  test("An accepted submission is scored, remembered and handed to persistence once") {
    val accepted = mutableListOf<AcceptedSubmission>()
    val state = RoundState(GeneratedRound(record(), emptyList())) { accepted += it }
    val acceptedAt = StartsAt.plusSeconds(5)

    val outcome = state.submit("player-1", listOf(0, 5, 4, 1), lexicon(), acceptedAt)

    outcome.shouldBeInstanceOf<SubmitOutcome.Accepted>()
    outcome.normalized shouldBe "LIMO"
    outcome.display shouldBe "limo"
    outcome.score shouldBe 17 // 10 + 2 + 3 + 2
    outcome.path shouldContainExactly listOf(0, 5, 4, 1)
    outcome.runningScore shouldBe 17
    outcome.runningWords shouldBe 1

    accepted shouldContainExactly listOf(
      AcceptedSubmission("round-1", "player-1", "LIMO", "limo", 17, listOf(0, 5, 4, 1), acceptedAt),
    )
  }

  test("The same word a second time is rejected as already found and never persisted twice") {
    val accepted = mutableListOf<AcceptedSubmission>()
    val state = RoundState(GeneratedRound(record(), emptyList())) { accepted += it }

    state.submit("player-1", listOf(0, 5, 4, 1), lexicon(), StartsAt)
    val outcome = state.submit("player-1", listOf(0, 5, 4, 1), lexicon(), StartsAt.plusSeconds(1))

    outcome.shouldBeInstanceOf<SubmitOutcome.Rejected>()
    outcome.reason shouldBe RejectionReason.AlreadyFound
    outcome.path shouldContainExactly listOf(0, 5, 4, 1)
    accepted.size shouldBe 1
  }

  test("A rejected submission never reaches persistence") {
    val accepted = mutableListOf<AcceptedSubmission>()
    val state = RoundState(GeneratedRound(record(), emptyList())) { accepted += it }

    // 0 and 11 are not adjacent, so the path itself is invalid.
    val outcome = state.submit("player-1", listOf(0, 11), lexicon(), StartsAt)

    outcome.shouldBeInstanceOf<SubmitOutcome.Rejected>()
    outcome.reason shouldBe RejectionReason.InvalidPath
    accepted.shouldBeEmpty()
  }

  test("Each player scores on their own, and one player's word does not block another's") {
    val state = RoundState(GeneratedRound(record(), emptyList())) { }

    state.submit("player-1", listOf(0, 5, 4, 1), lexicon(), StartsAt)
    val other = state.submit("player-2", listOf(0, 5, 4, 1), lexicon(), StartsAt)

    other.shouldBeInstanceOf<SubmitOutcome.Accepted>()
    other.runningScore shouldBe 17
    other.runningWords shouldBe 1
  }

  test("Running totals add up across different words") {
    val state = RoundState(GeneratedRound(record(), emptyList())) { }

    state.submit("player-1", listOf(0, 5, 4, 1), lexicon(), StartsAt)
    val second = state.submit("player-1", listOf(4, 5, 0), lexicon(), StartsAt.plusSeconds(1))

    second.shouldBeInstanceOf<SubmitOutcome.Accepted>()
    second.normalized shouldBe "MIL"
    second.score shouldBe 15 // 3 + 2 + 10
    second.runningScore shouldBe 32
    second.runningWords shouldBe 2
  }

  test("A player's snapshot lists what they found, oldest first, with their running totals") {
    val state = RoundState(GeneratedRound(record(), emptyList())) { }
    state.submit("player-1", listOf(0, 5, 4, 1), lexicon(), StartsAt)
    state.submit("player-1", listOf(4, 5, 0), lexicon(), StartsAt.plusSeconds(1))

    val snapshot = state.playerState("player-1").snapshot()

    snapshot.found.map { it.normalized } shouldContainExactly listOf("LIMO", "MIL")
    snapshot.found.first().display shouldBe "limo"
    snapshot.found.first().score shouldBe 17
    snapshot.found.first().path shouldContainExactly listOf(0, 5, 4, 1)
    snapshot.found.first().acceptedAt shouldBe StartsAt
    snapshot.runningScore shouldBe 32
    snapshot.runningWords shouldBe 2
  }

  test("playerStateOrNull only answers for a player who already submitted") {
    val state = RoundState(GeneratedRound(record(), emptyList())) { }

    state.playerStateOrNull("player-1").shouldBeNull()
    state.playerState("player-1")
    state.playerStateOrNull("player-1").shouldNotBeNull()
  }

  test("restore rebuilds the running totals a restart lost, and duplicates still get caught") {
    val state = RoundState(GeneratedRound(record(), emptyList())) { }
    val playerState = state.playerState("player-1")

    playerState.restore(
      listOf(
        FoundWord("LIMO", "limo", 17, listOf(0, 5, 4, 1), StartsAt),
        FoundWord("MIL", "mil", 15, listOf(4, 5, 0), StartsAt.plusSeconds(1)),
      ),
    )

    val snapshot = playerState.snapshot()
    snapshot.runningScore shouldBe 32
    snapshot.runningWords shouldBe 2

    val outcome = state.submit("player-1", listOf(0, 5, 4, 1), lexicon(), StartsAt.plusSeconds(2))
    outcome.shouldBeInstanceOf<SubmitOutcome.Rejected>()
    outcome.reason shouldBe RejectionReason.AlreadyFound
  }

  test("restore is idempotent on the same word: the score is not counted twice") {
    val state = RoundState(GeneratedRound(record(), emptyList())) { }
    val playerState = state.playerState("player-1")
    val word = FoundWord("LIMO", "limo", 17, listOf(0, 5, 4, 1), StartsAt)

    playerState.restore(listOf(word))
    playerState.restore(listOf(word))

    playerState.snapshot().runningScore shouldBe 17
    playerState.snapshot().runningWords shouldBe 1
  }

  test("Participants are the players marked as such, each with their own entry time") {
    val state = RoundState(GeneratedRound(record(), emptyList())) { }
    val lateEntry = StartsAt.plusSeconds(40)

    state.markParticipant("player-1", StartsAt)
    state.markParticipant("player-2", lateEntry)

    state.isParticipant("player-1") shouldBe true
    state.isParticipant("player-2") shouldBe true
    state.isParticipant("player-3") shouldBe false
    state.participantSnapshot() shouldBe setOf("player-1", "player-2")
    state.entryTimeOf("player-1") shouldBe StartsAt
    state.entryTimeOf("player-2") shouldBe lateEntry
    state.entryTimeOf("player-3").shouldBeNull()
  }

  test("Submitting does not make a player a participant on its own") {
    val state = RoundState(GeneratedRound(record(), emptyList())) { }

    state.submit("player-1", listOf(0, 5, 4, 1), lexicon(), StartsAt)

    state.isParticipant("player-1") shouldBe false
    state.participantSnapshot().shouldBeEmpty()
  }

  test("The participant snapshot is a copy, unaffected by a later join") {
    val state = RoundState(GeneratedRound(record(), emptyList())) { }
    state.markParticipant("player-1", StartsAt)

    val snapshot = state.participantSnapshot()
    state.markParticipant("player-2", StartsAt)

    snapshot shouldBe setOf("player-1")
  }
})
