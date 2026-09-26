// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.lobby

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.palavramento.R
import br.com.colman.palavramento.ads.Ads
import br.com.colman.palavramento.settings.SettingsRepository
import br.com.colman.palavramento.ui.theme.PalavramentoColors
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The in-app notice the privacy policy promised before any ad appeared (ADR 0025): says the app now
 * shows ads, links to the updated policy, and never comes back once dismissed. Starts hidden, so a
 * player who already dismissed it never sees it flash in while the setting loads.
 */
@Composable
fun AdsNotice(settings: SettingsRepository = koinInject()) {
  val dismissed by settings.adsNoticeDismissed.collectAsState(initial = true)
  if (dismissed) return
  val colors = PalavramentoColors.current
  val scope = rememberCoroutineScope()
  val uriHandler = LocalUriHandler.current
  val policyUrl = stringResource(R.string.ads_notice_policy_url)
  Column(
    Modifier
      .fillMaxWidth()
      .background(colors.surface, RoundedCornerShape(12.dp))
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(stringResource(R.string.ads_notice_title), color = colors.textPrimary)
    Text(stringResource(R.string.ads_notice_body), color = colors.textSecondary)
    Column(horizontalAlignment = Alignment.Start) {
      TextButton(onClick = { uriHandler.openUri(policyUrl) }) {
        Text(stringResource(R.string.ads_notice_policy_button))
      }
      TextButton(onClick = { scope.launch { settings.setAdsNoticeDismissed() } }) {
        Text(stringResource(R.string.ads_notice_dismiss))
      }
    }
  }
}

/**
 * "Privacidade dos anúncios": reopens the consent form. Only where the law gives the player that
 * right (the consent SDK says when), so everyone else never sees an option that does nothing.
 */
@Composable
fun AdPrivacyOptionsButton(ads: Ads = koinInject()) {
  val required by ads.privacyOptionsRequired.collectAsState()
  val activity = LocalActivity.current
  if (!required || activity == null) return
  val colors = PalavramentoColors.current
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    TextButton(onClick = { ads.showPrivacyOptions(activity) }) {
      Text(stringResource(R.string.ads_privacy_options_button), color = colors.textDisabled)
    }
  }
}
