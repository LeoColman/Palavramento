// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import br.com.colman.palavramento.BuildConfig
import br.com.colman.palavramento.ads.Ads
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import org.koin.compose.koinInject

/**
 * The anchored adaptive banner at the bottom of the lobby and of the results screen (ADR 0025). Takes
 * no room at all until [Ads.ready], so a player who has not answered the consent form, or is offline,
 * sees the screen exactly as before. Never placed on the match screen: a banner right under a board
 * the player drags across is where accidental taps come from.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier, ads: Ads = koinInject()) {
  val ready by ads.ready.collectAsState()
  if (!ready) return

  val context = LocalContext.current
  val widthDp = LocalConfiguration.current.screenWidthDp
  val adView = remember(widthDp) {
    AdView(context).apply {
      adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
      setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
    }
  }
  DisposableEffect(adView) {
    adView.loadAd(AdRequest.Builder().build())
    onDispose { adView.destroy() }
  }
  AndroidView(factory = { adView }, modifier = modifier.fillMaxWidth())
}
