// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.di

import br.com.colman.palavramento.ads.Ads
import br.com.colman.palavramento.ads.GmsAds
import org.koin.dsl.module

/**
 * AdMob (ADR 0025). Its own module, like [AudioModule], because [GmsAds] reaches Google's consent SDK
 * as soon as it is built, which [AppModuleTest][br.com.colman.palavramento.di.AppModuleTest]'s
 * plain-JVM `checkModules()` cannot do. `single`: consent and the SDK start once per process.
 */
val AdsModule = module {
  single<Ads> { GmsAds(get()) }
}
