// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.stats

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RankingTest : FunSpec({
  test("Higher score ranks first") {
    val entries = listOf(
      LeaderboardEntry("p1", "Ana", score = 50, words = 5),
      LeaderboardEntry("p2", "Bia", score = 100, words = 5),
    )
    val ranked = Ranking.rank(entries)
    ranked.first().entry.playerId shouldBe "p2"
    ranked.first().entry.name shouldBe "Bia"
    ranked.first().rank shouldBe 1
    ranked.last().rank shouldBe 2
  }

  test("A score tie is broken by more words found") {
    val entries = listOf(
      LeaderboardEntry("p1", "Ana", score = 100, words = 5),
      LeaderboardEntry("p2", "Bia", score = 100, words = 8),
    )
    Ranking.rank(entries).first().entry.playerId shouldBe "p2"
  }

  test("A score and word tie is broken by player id, ascending") {
    val entries = listOf(
      LeaderboardEntry("zeta", "Ana", score = 100, words = 5),
      LeaderboardEntry("alpha", "Bia", score = 100, words = 5),
    )
    Ranking.rank(entries).first().entry.playerId shouldBe "alpha"
  }

  test("Ranks are 1-based and contiguous") {
    val entries = (1..5).map { LeaderboardEntry("p$it", "P$it", score = it, words = it) }
    Ranking.rank(entries).map { it.rank } shouldBe listOf(1, 2, 3, 4, 5)
  }

  test("Ranking is deterministic regardless of input order") {
    val entries = listOf(
      LeaderboardEntry("p1", "Ana", 100, 5),
      LeaderboardEntry("p2", "Bia", 80, 5),
      LeaderboardEntry("p3", "Caio", 100, 5),
    )
    Ranking.rank(entries) shouldBe Ranking.rank(entries.reversed())
  }
})
