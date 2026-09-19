// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.repository

import br.com.colman.palavramento.server.testsupport.insertGuest
import br.com.colman.palavramento.server.testsupport.testDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/** CRUD plus incremental aggregation over `player_stats` (dossier §7, ADR 0007's rebuild path). */
class PlayerStatsRepositoryTest : FunSpec({
  val database = testDatabase()

  test("get returns null for a player with no stats row yet") {
    val playerRepository = PlayerRepository(database)
    val repository = PlayerStatsRepository(database)
    val player = playerRepository.insertGuest()

    repository.get(player.id).shouldBeNull()
  }

  test("applyRound creates a fresh row on the player's first round, marking it completed when words were found") {
    val playerRepository = PlayerRepository(database)
    val repository = PlayerStatsRepository(database)
    val player = playerRepository.insertGuest()
    val contribution = RoundContribution(
      score = 30,
      words = 2,
      bestWordDisplay = "casa",
      bestWordScore = 15,
      rank = 3,
      xp = 6,
    )

    playerRepository.transaction { repository.applyRound(this, player.id, contribution) }

    val stats = repository.get(player.id)
    stats.shouldNotBeNull()
    stats.totalScore shouldBe 30L
    stats.totalWords shouldBe 2L
    stats.bestGameScore shouldBe 30
    stats.bestWord shouldBe "casa"
    stats.bestWordScore shouldBe 15
    stats.gamesPlayed shouldBe 1
    stats.gamesCompleted shouldBe 1
    stats.bestRank shouldBe 3
    stats.totalXp shouldBe 6L
  }

  test("applyRound's first row counts as not completed when exactly zero words were found") {
    val playerRepository = PlayerRepository(database)
    val repository = PlayerStatsRepository(database)
    val player = playerRepository.insertGuest()
    val contribution =
      RoundContribution(score = 0, words = 0, bestWordDisplay = null, bestWordScore = 0, rank = 8, xp = 0)

    playerRepository.transaction { repository.applyRound(this, player.id, contribution) }

    val stats = repository.get(player.id)
    stats?.gamesPlayed shouldBe 1
    stats?.gamesCompleted shouldBe 0
    stats?.bestWord.shouldBeNull()
  }

  test("applyRound on an existing row sums totals, counts games and only raises bestGameScore, never lowers it") {
    val playerRepository = PlayerRepository(database)
    val repository = PlayerStatsRepository(database)
    val player = playerRepository.insertGuest()
    val first = RoundContribution(score = 20, words = 1, bestWordDisplay = "sol", bestWordScore = 10, rank = 2, xp = 4)
    val higher = RoundContribution(score = 50, words = 3, bestWordDisplay = "mar", bestWordScore = 5, rank = 1, xp = 9)
    val lower = RoundContribution(score = 10, words = 0, bestWordDisplay = "oi", bestWordScore = 1, rank = 4, xp = 1)

    playerRepository.transaction { repository.applyRound(this, player.id, first) }
    playerRepository.transaction { repository.applyRound(this, player.id, higher) }
    playerRepository.transaction { repository.applyRound(this, player.id, lower) }

    val stats = repository.get(player.id)
    stats?.totalScore shouldBe 80L
    stats?.totalWords shouldBe 4L
    stats?.totalXp shouldBe 14L
    // Highest single-round score across the three (50), never overwritten by the later, lower one.
    stats?.bestGameScore shouldBe 50
    stats?.gamesPlayed shouldBe 3
    // Only "first" and "higher" had words > 0.
    stats?.gamesCompleted shouldBe 2
  }

  test("applyRound updates bestWord/bestWordScore only on a strictly higher score, keeping the old one on a tie") {
    val playerRepository = PlayerRepository(database)
    val repository = PlayerStatsRepository(database)
    val player = playerRepository.insertGuest()
    val first =
      RoundContribution(score = 1, words = 1, bestWordDisplay = "primeira", bestWordScore = 10, rank = 1, xp = 1)
    val tie =
      RoundContribution(score = 1, words = 1, bestWordDisplay = "empate", bestWordScore = 10, rank = 1, xp = 1)
    val higher =
      RoundContribution(score = 1, words = 1, bestWordDisplay = "maior", bestWordScore = 11, rank = 1, xp = 1)

    playerRepository.transaction { repository.applyRound(this, player.id, first) }
    playerRepository.transaction { repository.applyRound(this, player.id, tie) }
    var stats = repository.get(player.id)
    // A tie does not replace the incumbent: strict '>' only.
    stats?.bestWord shouldBe "primeira"
    stats?.bestWordScore shouldBe 10

    playerRepository.transaction { repository.applyRound(this, player.id, higher) }
    stats = repository.get(player.id)
    stats?.bestWord shouldBe "maior"
    stats?.bestWordScore shouldBe 11
  }

  test("applyRound's bestRank starts from a null existing rank, then only ever takes the minimum seen") {
    val playerRepository = PlayerRepository(database)
    val repository = PlayerStatsRepository(database)
    val player = playerRepository.insertGuest()
    // A row with no rank yet, as a migration rebuild starting from zero rounds would leave it.
    repository.replace(
      PlayerStatsRow(
        playerId = player.id,
        totalScore = 0,
        totalWords = 0,
        bestGameScore = 0,
        bestWord = null,
        bestWordScore = 0,
        gamesPlayed = 0,
        gamesCompleted = 0,
        bestRank = null,
        totalXp = 0,
      ),
    )

    val enteringAtRankFive =
      RoundContribution(score = 1, words = 1, bestWordDisplay = null, bestWordScore = 0, rank = 5, xp = 0)
    playerRepository.transaction { repository.applyRound(this, player.id, enteringAtRankFive) }
    repository.get(player.id)?.bestRank shouldBe 5

    val worseRankEight =
      RoundContribution(score = 1, words = 1, bestWordDisplay = null, bestWordScore = 0, rank = 8, xp = 0)
    playerRepository.transaction { repository.applyRound(this, player.id, worseRankEight) }
    // Worse (higher) rank number does not replace the better one already recorded.
    repository.get(player.id)?.bestRank shouldBe 5

    val betterRankOne =
      RoundContribution(score = 1, words = 1, bestWordDisplay = null, bestWordScore = 0, rank = 1, xp = 0)
    playerRepository.transaction { repository.applyRound(this, player.id, betterRankOne) }
    repository.get(player.id)?.bestRank shouldBe 1
  }

  test("replace overwrites the row outright, not merging with whatever was there before") {
    val playerRepository = PlayerRepository(database)
    val repository = PlayerStatsRepository(database)
    val player = playerRepository.insertGuest()
    repository.replace(
      PlayerStatsRow(
        playerId = player.id,
        totalScore = 100,
        totalWords = 10,
        bestGameScore = 40,
        bestWord = "antigo",
        bestWordScore = 9,
        gamesPlayed = 5,
        gamesCompleted = 4,
        bestRank = 2,
        totalXp = 50,
      ),
    )

    repository.replace(
      PlayerStatsRow(
        playerId = player.id,
        totalScore = 7,
        totalWords = 1,
        bestGameScore = 7,
        bestWord = null,
        bestWordScore = 0,
        gamesPlayed = 1,
        gamesCompleted = 0,
        bestRank = null,
        totalXp = 3,
      ),
    )

    val stats = repository.get(player.id)
    stats.shouldNotBeNull()
    stats.totalScore shouldBe 7L
    stats.totalWords shouldBe 1L
    stats.bestGameScore shouldBe 7
    stats.bestWord.shouldBeNull()
    stats.bestWordScore shouldBe 0
    stats.gamesPlayed shouldBe 1
    stats.gamesCompleted shouldBe 0
    stats.bestRank.shouldBeNull()
    stats.totalXp shouldBe 3L
  }

  test("delete removes the row: get then returns null") {
    val playerRepository = PlayerRepository(database)
    val repository = PlayerStatsRepository(database)
    val player = playerRepository.insertGuest()
    repository.replace(
      PlayerStatsRow(
        playerId = player.id,
        totalScore = 1,
        totalWords = 1,
        bestGameScore = 1,
        bestWord = "x",
        bestWordScore = 1,
        gamesPlayed = 1,
        gamesCompleted = 1,
        bestRank = 1,
        totalXp = 1,
      ),
    )

    repository.delete(player.id)

    repository.get(player.id).shouldBeNull()
  }
})
