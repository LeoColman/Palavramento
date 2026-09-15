// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.protocol

import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.solver.WordTier
import br.com.colman.palavramento.domain.stats.RoundStats
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.KSerializer

private fun <T> roundTrip(serializer: KSerializer<T>, value: T): T =
  PalavramentoJson.decodeFromString(serializer, PalavramentoJson.encodeToString(serializer, value))

class RestContractsTest : FunSpec({
  test("Auth requests and tokens round-trip") {
    roundTrip(GuestAuthRequest.serializer(), GuestAuthRequest()) shouldBe GuestAuthRequest(null)
    roundTrip(GuestAuthRequest.serializer(), GuestAuthRequest("Ana")) shouldBe GuestAuthRequest("Ana")
    roundTrip(RefreshRequest.serializer(), RefreshRequest("r")) shouldBe RefreshRequest("r")
    val register = RegisterRequest("ana@example.com", "segredo123", "Ana")
    roundTrip(RegisterRequest.serializer(), register) shouldBe register
    val login = LoginRequest("ana@example.com", "segredo123")
    roundTrip(LoginRequest.serializer(), login) shouldBe login
    val tokens = AuthTokens("p1", "Convidado", isGuest = true, "jwt", 1_700_000_000_000, "refresh")
    roundTrip(AuthTokens.serializer(), tokens) shouldBe tokens
  }

  test("Profile and lifetime stats round-trip, including absent best word and rank") {
    val profile = PlayerProfile("p1", "Ana", isGuest = false, level = 3, totalXp = 640, xpForNextLevel = 800)
    roundTrip(PlayerProfile.serializer(), profile) shouldBe profile
    val fresh = LifetimeStats(0, 0, 0, null, 0, 0, 0, 0.0, 0.0, 0.0, null)
    roundTrip(LifetimeStats.serializer(), fresh) shouldBe fresh
    val veteran = LifetimeStats(4193, 272, 900, "pêssego", 25, 10, 12, 419.3, 27.2, 15.4, 1)
    roundTrip(LifetimeStats.serializer(), veteran) shouldBe veteran
  }

  test("A history entry carries the board, the mutator and the labelled words") {
    val entry = RoundHistoryEntry(
      roundId = "r1",
      startsAt = 1_700_000_000_000,
      board = List(16) { Tile(('A' + it).toString(), it + 1) },
      mutator = Mutator.ValuableLetter('L', 10),
      themeTitle = "L de alto valor",
      themeSubtitle = "19 palavras comuns",
      maxScore = 4193,
      maxWords = 272,
      stats = RoundStats(73, 6, 1.0, 3.5, 0, 12.2, 14),
      rank = 2,
      totalPlayers = 10,
      words = listOf(LabelledWord("limo", 17, WordTier.Common, listOf(0, 5, 4, 1), found = true)),
    )
    roundTrip(RoundHistoryEntry.serializer(), entry) shouldBe entry
    PalavramentoJson.encodeToString(RoundHistoryEntry.serializer(), entry) shouldContain "\"LETRA_VALIOSA\""
  }
})
