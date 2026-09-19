// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.data

import br.com.colman.palavramento.domain.protocol.AuthTokens
import br.com.colman.palavramento.domain.protocol.LifetimeStats
import br.com.colman.palavramento.domain.protocol.PlayerProfile
import br.com.colman.palavramento.domain.protocol.RoundHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [TokenRepository] for [AuthController]/[SyncService]/view model tests. */
class FakeTokenRepository(initial: AuthTokens? = null, sessionExpired: Boolean = false) : TokenRepository {
  private val state = MutableStateFlow(initial)
  override val tokens: Flow<AuthTokens?> = state

  private val sessionExpiredState = MutableStateFlow(sessionExpired)
  override val sessionExpired: Flow<Boolean> = sessionExpiredState

  override suspend fun save(tokens: AuthTokens) {
    state.value = tokens
  }

  override suspend fun clear() {
    state.value = null
  }

  override suspend fun setSessionExpired(expired: Boolean) {
    sessionExpiredState.value = expired
  }
}

/** In-memory [ProfileRepository] for [AuthController]/[SyncService]/view model tests, tracking [clearCount]. */
class FakeProfileRepository : ProfileRepository {
  private val profileState = MutableStateFlow<PlayerProfile?>(null)
  private val statsState = MutableStateFlow<LifetimeStats?>(null)

  var clearCount = 0
    private set

  override fun profile(): Flow<PlayerProfile?> = profileState
  override fun stats(): Flow<LifetimeStats?> = statsState

  override suspend fun saveProfile(profile: PlayerProfile) {
    profileState.value = profile
  }

  override suspend fun saveStats(stats: LifetimeStats) {
    statsState.value = stats
  }

  override suspend fun clear() {
    clearCount++
    profileState.value = null
    statsState.value = null
  }
}

/** In-memory [HistoryRepository] for [AuthController]/[SyncService]/view model tests, tracking [clearCount]. */
class FakeHistoryRepository : HistoryRepository {
  private val roundsState = MutableStateFlow<List<RoundHistoryEntry>>(emptyList())

  var clearCount = 0
    private set

  override fun rounds(): Flow<List<RoundHistoryEntry>> = roundsState

  override fun round(roundId: String): Flow<RoundHistoryEntry?> =
    roundsState.map { list -> list.find { it.roundId == roundId } }

  override suspend fun upsertAll(entries: List<RoundHistoryEntry>) {
    roundsState.value = entries
  }

  override suspend fun clear() {
    clearCount++
    roundsState.value = emptyList()
  }
}
