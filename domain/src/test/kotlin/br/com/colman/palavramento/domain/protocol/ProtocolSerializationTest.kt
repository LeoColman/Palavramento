// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.protocol

import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.solver.WordTier
import br.com.colman.palavramento.domain.stats.RoundStats
import br.com.colman.palavramento.domain.submission.RejectionReason
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun sampleBoard() = List(4) { Tile(('A' + it).toString(), it + 1) }

private fun sampleStats() = RoundStats(
  points = 73,
  words = 6,
  secondsPerWord = 1.0,
  averageLength = 3.5,
  bonusPoints = 0,
  averagePoints = 12.2,
  xp = 14,
)

private fun roundTrip(message: ServerMessage): ServerMessage {
  val json = PalavramentoJson.encodeToString(ServerMessage.serializer(), message)
  return PalavramentoJson.decodeFromString(ServerMessage.serializer(), json)
}

private fun roundTrip(message: ClientMessage): ClientMessage {
  val json = PalavramentoJson.encodeToString(ClientMessage.serializer(), message)
  return PalavramentoJson.decodeFromString(ClientMessage.serializer(), json)
}

class ProtocolSerializationTest : FunSpec({
  test("LobbyState round-trips") {
    val message = ServerMessage.LobbyState(nextRoundStartsAt = 1_700_000_000_000, playersWaiting = 3)
    roundTrip(message) shouldBe message
  }

  test("RoundStart round-trips, including reconnection fields") {
    val message = ServerMessage.RoundStart(
      roundId = "round-1",
      board = sampleBoard(),
      mutator = Mutator.ValuableLetter('L', 10),
      themeTitle = "L de alto valor",
      themeSubtitle = "19 palavras comuns",
      maxScore = 4193,
      maxWords = 272,
      startsAt = 1_000,
      endsAt = 121_000,
      alreadyFound = listOf(FoundWord("limo", 17, listOf(0, 5, 4, 1))),
      runningScore = 17,
      runningWords = 1,
    )
    roundTrip(message) shouldBe message
  }

  test("RoundStart defaults its reconnection fields to empty/zero, and the wire still carries them") {
    val message = ServerMessage.RoundStart(
      roundId = "round-1",
      board = sampleBoard(),
      mutator = Mutator.NoMutator,
      themeTitle = "Grade padrão",
      themeSubtitle = "15 palavras comuns",
      maxScore = 4193,
      maxWords = 272,
      startsAt = 1_000,
      endsAt = 121_000,
    )
    val json = PalavramentoJson.encodeToString(ServerMessage.serializer(), message)
    json shouldContain "\"runningScore\":0"
    roundTrip(message) shouldBe message
  }

  test("WordAccepted round-trips, echoing the path") {
    val message = ServerMessage.WordAccepted(
      "limo",
      score = 17,
      runningScore = 17,
      runningWords = 1,
      path = listOf(0, 5, 4, 1)
    )
    roundTrip(message) shouldBe message
  }

  test("WordRejected round-trips for every reason") {
    RejectionReason.entries.forEach { reason ->
      val message = ServerMessage.WordRejected(reason, path = listOf(0, 1))
      roundTrip(message) shouldBe message
    }
  }

  test("Every mutator round-trips through JSON, unchanged") {
    listOf(
      Mutator.NoMutator,
      Mutator.ValuableLetter('L', 10),
      Mutator.Digraphs(3),
      Mutator.LetterInCorners('O'),
    ).forEach { mutator ->
      val json = PalavramentoJson.encodeToString(Mutator.serializer(), mutator)
      PalavramentoJson.decodeFromString(Mutator.serializer(), json) shouldBe mutator
    }
  }

  test("Every mutator carries the dossier's wire token as its type discriminator (ADR 0012)") {
    val cases = mapOf(
      "SEM_MUTADOR" to Mutator.NoMutator,
      "LETRA_VALIOSA" to Mutator.ValuableLetter('L', 10),
      "DIGRAFOS" to Mutator.Digraphs(3),
      "LETRA_NOS_CANTOS" to Mutator.LetterInCorners('O'),
    )
    cases.forEach { (type, mutator) ->
      PalavramentoJson.encodeToString(Mutator.serializer(), mutator) shouldContain "\"type\":\"$type\""
    }
  }

  test("RoundEnd round-trips its labelled word list") {
    val message = ServerMessage.RoundEnd(
      roundId = "round-1",
      stats = sampleStats(),
      words = listOf(
        LabelledWord("limo", 17, WordTier.Common, listOf(0, 5, 4, 1), found = true),
        LabelledWord("pilo", 19, WordTier.Expert, listOf(8, 5, 0, 1), found = false),
      ),
    )
    roundTrip(message) shouldBe message
  }

  test("Leaderboard round-trips") {
    val message = ServerMessage.Leaderboard(
      players = listOf(LeaderboardRow(1, "Ana", 100, 8), LeaderboardRow(2, "Bia", 80, 6)),
      self = LeaderboardRow(2, "Bia", 80, 6),
      percentile = 50,
      totalPlayers = 2,
    )
    roundTrip(message) shouldBe message
  }

  test("ClockSyncResponse round-trips") {
    val message = ServerMessage.ClockSyncResponse(clientSentAt = 1_000, serverTime = 1_050)
    roundTrip(message) shouldBe message
  }

  test("JoinRoom round-trips, with its pt-BR default language and optional token") {
    roundTrip(ClientMessage.JoinRoom()) shouldBe ClientMessage.JoinRoom(languageCode = "pt-BR", sessionToken = null)
    val withToken = ClientMessage.JoinRoom(sessionToken = "abc123")
    roundTrip(withToken) shouldBe withToken
  }

  test("SubmitWord round-trips") {
    val message = ClientMessage.SubmitWord(roundId = "round-1", path = listOf(0, 5, 4, 1), clientTimestamp = 5_000)
    roundTrip(message) shouldBe message
  }

  test("LeaveRoom round-trips") {
    roundTrip(ClientMessage.LeaveRoom) shouldBe ClientMessage.LeaveRoom
  }

  test("ClockSync round-trips") {
    val message = ClientMessage.ClockSync(clientSentAt = 1_000)
    roundTrip(message) shouldBe message
  }

  test("Every server message carries the dossier's message name as its type discriminator") {
    val cases = mapOf(
      "LobbyState" to ServerMessage.LobbyState(0, 0),
      "WordAccepted" to ServerMessage.WordAccepted("a", 1, 1, 1, listOf(0)),
      "ClockSyncResponse" to ServerMessage.ClockSyncResponse(0, 0),
    )
    cases.forEach { (type, message) ->
      PalavramentoJson.encodeToString(ServerMessage.serializer(), message) shouldContain "\"type\":\"$type\""
    }
  }

  test("Unknown fields in incoming JSON are ignored, not fatal") {
    val json = """{"type":"LeaveRoom","somethingNew":42}"""
    PalavramentoJson.decodeFromString(ClientMessage.serializer(), json) shouldBe ClientMessage.LeaveRoom
  }
})
