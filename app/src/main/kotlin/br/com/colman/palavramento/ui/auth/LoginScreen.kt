// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import br.com.colman.palavramento.R
import br.com.colman.palavramento.ui.theme.PalavramentoColors
import org.koin.androidx.compose.koinViewModel

/** Test tag for the submit button, used by instrumented tests. */
const val LoginSubmitButtonTestTag = "loginSubmitButton"

/** Test tag for the back button, used by instrumented navigation tests. */
const val AuthBackButtonTestTag = "authBackButton"

/**
 * Login/registration screen (dossier 8, task brief deliverable 4): opened from the lobby's "Entrar"
 * button. Registering while a guest promotes that same player (history kept); logging in while a
 * guest migrates that guest's history into the account (ADR 0007) - both paths go through
 * [LoginViewModel]/[br.com.colman.palavramento.data.AuthController], this screen only renders the
 * form and reacts to [LoginUiState.success].
 */
@Composable
fun LoginScreen(onBack: () -> Unit, onAuthenticated: () -> Unit, viewModel: LoginViewModel = koinViewModel()) {
  val uiState by viewModel.uiState.collectAsState()
  val colors = PalavramentoColors.current

  LaunchedEffect(uiState.success) {
    if (uiState.success) onAuthenticated()
  }

  Column(
    Modifier
      .fillMaxSize()
      .background(colors.background)
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(16.dp),
  ) {
    LoginHeader(onBack, uiState.mode)
    LoginForm(uiState, viewModel)
  }
}

@Composable
private fun LoginForm(uiState: LoginUiState, viewModel: LoginViewModel) {
  val colors = PalavramentoColors.current
  Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    OutlinedTextField(
      value = uiState.email,
      onValueChange = viewModel::onEmailChanged,
      label = { Text(stringResource(R.string.auth_email_label)) },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      value = uiState.password,
      onValueChange = viewModel::onPasswordChanged,
      label = { Text(stringResource(R.string.auth_password_label)) },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
      visualTransformation = PasswordVisualTransformation(),
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    if (uiState.mode == LoginMode.Register) {
      OutlinedTextField(
        value = uiState.displayName,
        onValueChange = viewModel::onDisplayNameChanged,
        label = { Text(stringResource(R.string.auth_display_name_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    }
    uiState.errorMessageRes?.let { messageRes -> Text(stringResource(messageRes), color = colors.rejected) }
    val isLoginMode = uiState.mode == LoginMode.Login
    val submitLabelRes = if (isLoginMode) R.string.auth_submit_login else R.string.auth_submit_register
    Button(
      onClick = viewModel::onSubmit,
      enabled = !uiState.isSubmitting,
      modifier = Modifier.fillMaxWidth().testTag(LoginSubmitButtonTestTag),
    ) {
      if (uiState.isSubmitting) {
        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
      }
      Text(stringResource(submitLabelRes))
    }
    val switchLabelRes = if (isLoginMode) R.string.auth_switch_to_register else R.string.auth_switch_to_login
    Text(
      stringResource(switchLabelRes),
      color = colors.matchAccent,
      modifier = Modifier.padding(top = 8.dp).clickable(onClick = viewModel::onModeToggled),
    )
  }
}

@Composable
private fun LoginHeader(onBack: () -> Unit, mode: LoginMode) {
  val colors = PalavramentoColors.current
  val backDescription = stringResource(R.string.auth_back_content_description)
  Row(verticalAlignment = Alignment.CenterVertically) {
    IconButton(
      onClick = onBack,
      modifier = Modifier.testTag(AuthBackButtonTestTag).semantics { contentDescription = backDescription },
    ) {
      Text("<", color = colors.textPrimary)
    }
    Text(
      stringResource(if (mode == LoginMode.Login) R.string.auth_title_login else R.string.auth_title_register),
      color = colors.textPrimary,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(start = 8.dp),
    )
  }
}
