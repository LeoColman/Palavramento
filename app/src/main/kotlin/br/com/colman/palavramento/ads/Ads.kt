// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ads

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/**
 * The app's side of AdMob (ADR 0025): asks for consent, starts the SDK once consent allows it, and
 * says when a banner may load. Behind an interface so screens and tests never touch the Google SDK.
 */
interface Ads {

  /** True once consent allows requesting ads and the SDK is initialized. Banners wait for this. */
  val ready: StateFlow<Boolean>

  /**
   * True when the player is somewhere consent is regulated (the EEA, the UK, Switzerland) and so must
   * be able to revisit their choice from inside the app. The lobby shows the entry point only then.
   */
  val privacyOptionsRequired: StateFlow<Boolean>

  /** Asks for consent when needed, then starts the SDK. Called by every activity start; runs once. */
  fun start(activity: Activity)

  /** Opens the consent form again, so the player can change what they chose. */
  fun showPrivacyOptions(activity: Activity)
}
