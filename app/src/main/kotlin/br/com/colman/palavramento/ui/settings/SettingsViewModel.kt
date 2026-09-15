// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.palavramento.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the match settings sheet (task brief 3): the haptics on/off toggle. */
class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

  val hapticsEnabled: StateFlow<Boolean> = repository.hapticsEnabled
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SubscriptionTimeoutMs), true)

  fun setHapticsEnabled(enabled: Boolean) {
    viewModelScope.launch { repository.setHapticsEnabled(enabled) }
  }

  private companion object {
    const val SubscriptionTimeoutMs = 5_000L
  }
}
