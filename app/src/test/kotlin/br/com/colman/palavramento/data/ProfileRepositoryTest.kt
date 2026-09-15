// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver.Companion.IN_MEMORY
import br.com.colman.palavramento.domain.protocol.LifetimeStats
import br.com.colman.palavramento.domain.protocol.PlayerProfile
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

private fun sampleProfile() = PlayerProfile(
  playerId = "p1",
  displayName = "Convidado ABCD",
  isGuest = true,
  level = 1,
  totalXp = 0,
  xpForNextLevel = 100,
)

private fun sampleStats() = LifetimeStats(
  totalScore = 150,
  totalWords = 15,
  bestGameScore = 100,
  bestWord = "casa",
  bestWordScore = 12,
  gamesCompleted = 2,
  gamesPlayed = 2,
  averageScore = 75.0,
  averageWords = 7.5,
  averagePointsPerWord = 10.0,
  bestRank = 1,
)

/** [SqlDelightProfileRepository] against the JVM SQLite driver (task brief: "repository tests on the JVM driver"). */
class ProfileRepositoryTest : FunSpec({

  fun newRepository(): ProfileRepository {
    val driver = JdbcSqliteDriver(IN_MEMORY)
    Database.Schema.create(driver)
    return SqlDelightProfileRepository(Database(driver))
  }

  test("profile and stats are null before anything is saved") {
    runTest {
      val repository = newRepository()
      repository.profile().first() shouldBe null
      repository.stats().first() shouldBe null
    }
  }

  test("saveProfile then profile reads back the same PlayerProfile") {
    runTest {
      val repository = newRepository()
      val profile = sampleProfile()

      repository.saveProfile(profile)

      repository.profile().first() shouldBe profile
    }
  }

  test("saveProfile again overwrites the single cached row, not appends to it") {
    runTest {
      val repository = newRepository()
      repository.saveProfile(sampleProfile())

      val promoted = sampleProfile().copy(isGuest = false, displayName = "Ana", level = 3, totalXp = 900)
      repository.saveProfile(promoted)

      repository.profile().first() shouldBe promoted
    }
  }

  test("saveStats then stats reads back the same LifetimeStats") {
    runTest {
      val repository = newRepository()
      val stats = sampleStats()

      repository.saveStats(stats)

      repository.stats().first() shouldBe stats
    }
  }

  test("saveStats preserves a null bestWord/bestRank") {
    runTest {
      val repository = newRepository()
      val stats = sampleStats().copy(bestWord = null, bestRank = null)

      repository.saveStats(stats)

      repository.stats().first() shouldBe stats
    }
  }

  test("clear wipes both the cached profile and stats") {
    runTest {
      val repository = newRepository()
      repository.saveProfile(sampleProfile())
      repository.saveStats(sampleStats())

      repository.clear()

      repository.profile().first() shouldBe null
      repository.stats().first() shouldBe null
    }
  }

  isolationMode = IsolationMode.InstancePerTest
})
