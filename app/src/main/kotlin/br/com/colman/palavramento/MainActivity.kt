// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import br.com.colman.palavramento.ui.navigation.PalavramentoNavHost
import br.com.colman.palavramento.ui.theme.PalavramentoTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      PalavramentoTheme {
        Surface(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
          PalavramentoNavHost()
        }
      }
    }
  }
}
