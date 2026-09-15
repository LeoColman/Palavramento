// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import br.com.colman.palavramento.R
import br.com.colman.palavramento.ui.theme.PalavramentoColors

/** Test tag for the back button, used by instrumented navigation tests. */
const val AboutBackButtonTestTag = "aboutBackButton"

/**
 * "Sobre" screen (task brief 6): the app's own AGPL-3.0-or-later notice with a link to the source,
 * plus the attributions `LICENSES.md` requires to appear here - the VERO Hunspell dictionary
 * (LGPLv3) and the FrequencyWords pt_BR frequency list (CC BY-SA 4.0), both behind the
 * comum/especialista split shown in Resultados (dossier 6.3).
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
  val colors = PalavramentoColors.current
  val uriHandler = LocalUriHandler.current
  val sourceUrl = stringResource(R.string.about_source_url)

  Column(Modifier.fillMaxSize().background(colors.background)) {
    AboutHeader(onBack)
    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      AboutSection(stringResource(R.string.about_license_title), stringResource(R.string.about_license_body))
      Text(
        stringResource(R.string.about_source_link_label),
        color = colors.matchAccent,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable { uriHandler.openUri(sourceUrl) },
      )
      AboutSection(stringResource(R.string.about_lexicon_title), stringResource(R.string.about_lexicon_body))
      AboutSection(stringResource(R.string.about_frequency_title), stringResource(R.string.about_frequency_body))
    }
  }
}

@Composable
private fun AboutHeader(onBack: () -> Unit) {
  val colors = PalavramentoColors.current
  val backDescription = stringResource(R.string.about_back_content_description)
  Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
    IconButton(
      onClick = onBack,
      modifier = Modifier.testTag(AboutBackButtonTestTag).semantics { contentDescription = backDescription },
    ) {
      Text("<", color = colors.textPrimary)
    }
    Text(
      stringResource(R.string.about_title),
      color = colors.textPrimary,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(start = 8.dp),
    )
  }
}

@Composable
private fun AboutSection(title: String, body: String) {
  val colors = PalavramentoColors.current
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold)
    Text(body, color = colors.textSecondary)
  }
}
