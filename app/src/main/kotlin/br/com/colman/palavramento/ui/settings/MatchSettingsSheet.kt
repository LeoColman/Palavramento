// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.palavramento.R
import br.com.colman.palavramento.ui.theme.PalavramentoColors
import org.koin.androidx.compose.koinViewModel

/** Test tag for the haptics toggle, used by instrumented tests. */
const val HapticsToggleTestTag = "hapticsToggle"

/** Test tag for the background music toggle, used by instrumented tests. */
const val MusicToggleTestTag = "musicToggle"

/** Test tag for the sound effects toggle, used by instrumented tests. */
const val EffectsToggleTestTag = "effectsToggle"

/** Test tag for the "Sobre" row, used by instrumented tests. */
const val AboutEntryTestTag = "aboutEntry"

/**
 * Match settings sheet (task brief 3, dossier 6.2 "ajustes"): opened from the match header's gear
 * icon. Holds the haptics, music and sound effect on/off toggles, plus the entry point to the
 * "Sobre" screen (task brief 6).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchSettingsSheet(
  onDismiss: () -> Unit,
  onAboutClick: () -> Unit,
  viewModel: SettingsViewModel = koinViewModel(),
) {
  val colors = PalavramentoColors.current
  val hapticsEnabled by viewModel.hapticsEnabled.collectAsState()
  val musicEnabled by viewModel.musicEnabled.collectAsState()
  val effectsEnabled by viewModel.effectsEnabled.collectAsState()
  val sheetState = rememberModalBottomSheetState()

  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.surface) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
      Text(stringResource(R.string.settings_title), color = colors.textPrimary)
      ToggleRow(R.string.settings_haptics_label, hapticsEnabled, viewModel::setHapticsEnabled, HapticsToggleTestTag)
      ToggleRow(R.string.settings_music_label, musicEnabled, viewModel::setMusicEnabled, MusicToggleTestTag)
      ToggleRow(R.string.settings_effects_label, effectsEnabled, viewModel::setEffectsEnabled, EffectsToggleTestTag)
      HorizontalDivider(color = colors.surfaceVariant)
      Row(
        Modifier
          .fillMaxWidth()
          .clickable(onClick = onAboutClick)
          .testTag(AboutEntryTestTag)
          .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(stringResource(R.string.settings_about_label), color = colors.textPrimary)
      }
    }
  }
}

/** One label + [Switch] row, the shape shared by the haptics/music/effects toggles above. */
@Composable
private fun ToggleRow(labelRes: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit, testTag: String) {
  val colors = PalavramentoColors.current
  Row(
    Modifier.fillMaxWidth().padding(vertical = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(stringResource(labelRes), color = colors.textPrimary)
    Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(testTag))
  }
}
