// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.palavramento.data.HistoryRepository
import br.com.colman.palavramento.domain.protocol.RoundHistoryEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * The cached round history (dossier 7, task brief 3), read straight from [historyRepository] -
 * nothing here touches the network, which is what makes "Historico visivel offline" (task brief
 * acceptance criterion) true for free: the list is already whatever [SyncService][br.com.colman.palavramento.data.SyncService]
 * last wrote, online or not.
 */
class HistoryViewModel(historyRepository: HistoryRepository) : ViewModel() {

  // Eagerly, not WhileSubscribed: the cache query is cheap (a local SQLite read), and starting it
  // right away means rounds already has the cached data the moment the screen first collects it,
  // with no "nobody has subscribed yet" gap to reason about.
  val rounds: StateFlow<List<RoundHistoryEntry>> = historyRepository.rounds()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
