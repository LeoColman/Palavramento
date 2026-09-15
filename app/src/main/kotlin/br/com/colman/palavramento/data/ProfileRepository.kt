// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import br.com.colman.palavramento.domain.protocol.LifetimeStats
import br.com.colman.palavramento.domain.protocol.PlayerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Local cache of [PlayerProfile] and [LifetimeStats] (dossier 7, ADR 0009): a single row each, for
 * whichever player this device is currently signed in as. [SyncService] is the only writer; the UI
 * (lobby header/stats panel, dossier 6.1) only ever reads through [profile]/[stats], so it keeps
 * showing the last known data offline instead of a blank screen.
 */
interface ProfileRepository {
  fun profile(): Flow<PlayerProfile?>
  fun stats(): Flow<LifetimeStats?>
  suspend fun saveProfile(profile: PlayerProfile)
  suspend fun saveStats(stats: LifetimeStats)

  /** Wipes both cached rows (logout, or a login that migrates into a different account). */
  suspend fun clear()
}

class SqlDelightProfileRepository(private val database: Database) : ProfileRepository {

  override fun profile(): Flow<PlayerProfile?> =
    database.profileQueries.selectProfile().asFlow().mapToOneOrNull(Dispatchers.IO).map { it?.toDomain() }

  override fun stats(): Flow<LifetimeStats?> =
    database.lifetimeStatsCacheQueries.selectStats().asFlow().mapToOneOrNull(Dispatchers.IO).map { it?.toDomain() }

  override suspend fun saveProfile(profile: PlayerProfile) = withContext(Dispatchers.IO) {
    database.profileQueries.upsert(
      playerId = profile.playerId,
      displayName = profile.displayName,
      isGuest = if (profile.isGuest) 1L else 0L,
      level = profile.level.toLong(),
      totalXp = profile.totalXp,
      xpForNextLevel = profile.xpForNextLevel,
    )
    Unit
  }

  override suspend fun saveStats(stats: LifetimeStats) = withContext(Dispatchers.IO) {
    database.lifetimeStatsCacheQueries.upsert(
      totalScore = stats.totalScore,
      totalWords = stats.totalWords,
      bestGameScore = stats.bestGameScore.toLong(),
      bestWord = stats.bestWord,
      bestWordScore = stats.bestWordScore.toLong(),
      gamesCompleted = stats.gamesCompleted.toLong(),
      gamesPlayed = stats.gamesPlayed.toLong(),
      averageScore = stats.averageScore,
      averageWords = stats.averageWords,
      averagePointsPerWord = stats.averagePointsPerWord,
      bestRank = stats.bestRank?.toLong(),
    )
    Unit
  }

  override suspend fun clear() = withContext(Dispatchers.IO) {
    database.profileQueries.clear()
    database.lifetimeStatsCacheQueries.clear()
    Unit
  }
}

private fun Profile.toDomain() = PlayerProfile(
  playerId = playerId,
  displayName = displayName,
  isGuest = isGuest != 0L,
  level = level.toInt(),
  totalXp = totalXp,
  xpForNextLevel = xpForNextLevel,
)

private fun LifetimeStatsCache.toDomain() = LifetimeStats(
  totalScore = totalScore,
  totalWords = totalWords,
  bestGameScore = bestGameScore.toInt(),
  bestWord = bestWord,
  bestWordScore = bestWordScore.toInt(),
  gamesCompleted = gamesCompleted.toInt(),
  gamesPlayed = gamesPlayed.toInt(),
  averageScore = averageScore,
  averageWords = averageWords,
  averagePointsPerWord = averagePointsPerWord,
  bestRank = bestRank?.toInt(),
)
