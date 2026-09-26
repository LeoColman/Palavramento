// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.lobby

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PlayStoreLinkTest : FunSpec({

  test("the invite points at the app's own Play Store page") {
    playStoreLink("br.com.colman.palavramento") shouldBe
      "https://play.google.com/store/apps/details?id=br.com.colman.palavramento"
  }
})
