// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [Ads] on Google's SDKs: the User Messaging Platform for consent, then Mobile Ads. Google requires
 * consent to be gathered before the first ad request, so [MobileAds.initialize] only runs once
 * [ConsentInformation.canRequestAds] says so. On later launches the stored consent answers at once.
 */
class GmsAds(private val appContext: Context) : Ads {

  private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(appContext)
  private val started = AtomicBoolean(false)
  private val initialized = AtomicBoolean(false)
  private val readyFlow = MutableStateFlow(false)
  private val privacyOptionsFlow = MutableStateFlow(false)

  override val ready: StateFlow<Boolean> = readyFlow.asStateFlow()
  override val privacyOptionsRequired: StateFlow<Boolean> = privacyOptionsFlow.asStateFlow()

  override fun start(activity: Activity) {
    if (!started.compareAndSet(false, true)) return
    consent.requestConsentInfoUpdate(
      activity,
      ConsentRequestParameters.Builder().build(),
      {
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { initializeIfAllowed() }
      },
      // No answer from Google this time (offline, say): consent stored earlier may still allow ads.
      { initializeIfAllowed() },
    )
    // Consent stored by an earlier launch: no need to wait for the update above.
    initializeIfAllowed()
  }

  override fun showPrivacyOptions(activity: Activity) {
    UserMessagingPlatform.showPrivacyOptionsForm(activity) { initializeIfAllowed() }
  }

  private fun initializeIfAllowed() {
    privacyOptionsFlow.value =
      consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    if (!consent.canRequestAds()) return
    if (initialized.compareAndSet(false, true)) {
      MobileAds.initialize(appContext) { readyFlow.value = true }
    }
  }
}
