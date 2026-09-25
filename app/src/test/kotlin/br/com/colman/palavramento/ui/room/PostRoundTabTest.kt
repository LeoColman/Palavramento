// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.room

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

class PostRoundTabTest : FunSpec({

  // Dossier 1.4: 25 seconds between rounds.
  val intermissionMs = 25_000L

  test("Resultados stays up for the first half of the intermission") {
    shouldAutoShowLeaderboard(
      remainingMs = 12_501,
      intermissionMs = intermissionMs,
      leaderboardReady = true,
      playerChoseTab = false,
    ) shouldBe false
  }

  test("Placar takes over exactly at the halfway point") {
    shouldAutoShowLeaderboard(
      remainingMs = 12_500,
      intermissionMs = intermissionMs,
      leaderboardReady = true,
      playerChoseTab = false,
    ) shouldBe true
  }

  test("Placar stays up for the rest of the intermission, down to zero") {
    shouldAutoShowLeaderboard(
      remainingMs = 0,
      intermissionMs = intermissionMs,
      leaderboardReady = true,
      playerChoseTab = false,
    ) shouldBe true
  }

  test("A tab the player picked is never overridden") {
    shouldAutoShowLeaderboard(
      remainingMs = 1_000,
      intermissionMs = intermissionMs,
      leaderboardReady = true,
      playerChoseTab = true,
    ) shouldBe false
  }

  test("Nothing flips before the Leaderboard message arrives") {
    shouldAutoShowLeaderboard(
      remainingMs = 1_000,
      intermissionMs = intermissionMs,
      leaderboardReady = false,
      playerChoseTab = false,
    ) shouldBe false
  }

  test("Nothing flips while the intermission length is still unknown") {
    shouldAutoShowLeaderboard(
      remainingMs = 0,
      intermissionMs = 0,
      leaderboardReady = true,
      playerChoseTab = false,
    ) shouldBe false
  }

  test("A countdown past its own intermission (a clock correction) still shows Resultados") {
    shouldAutoShowLeaderboard(
      remainingMs = 40_000,
      intermissionMs = intermissionMs,
      leaderboardReady = true,
      playerChoseTab = false,
    ) shouldBe false
  }

  test("Whatever the intermission, the flip happens once past its midpoint and never before") {
    checkAll(Arb.long(1L..120_000L), Arb.long(0L..120_000L)) { intermission, remaining ->
      shouldAutoShowLeaderboard(
        remainingMs = remaining,
        intermissionMs = intermission,
        leaderboardReady = true,
        playerChoseTab = false,
      ) shouldBe (remaining <= intermission / 2)
    }
  }

  test("The player's own choice wins for every countdown value") {
    checkAll(Arb.long(0L..120_000L)) { remaining ->
      shouldAutoShowLeaderboard(
        remainingMs = remaining,
        intermissionMs = intermissionMs,
        leaderboardReady = true,
        playerChoseTab = true,
      ) shouldBe false
    }
  }

  test("PostRoundTab ordinals match the tab order on screen: Resultados first, Placar second") {
    PostRoundTab.Results.ordinal shouldBe 0
    PostRoundTab.Leaderboard.ordinal shouldBe 1
  }
})
