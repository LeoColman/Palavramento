// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.palavramento.R
import br.com.colman.palavramento.data.AuthCallResult
import br.com.colman.palavramento.data.AuthController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Whether the form promotes/logs into an account (dossier 8): OIDC is out of scope, only e-mail/senha. */
enum class LoginMode { Login, Register }

/**
 * Login/registration form state (dossier 8, task brief deliverable 4). Client-side [validate] is
 * for UX only - "the server validates for real" (task brief) - so it never rejects anything the
 * server would accept; [AuthController] is what actually calls `/auth/register`/`/auth/login`,
 * carrying the current guest's access token so the server promotes/migrates that same guest instead
 * of the app trying to reason about it here.
 */
class LoginViewModel(private val authController: AuthController) : ViewModel() {

  private val mutableUiState = MutableStateFlow(LoginUiState())
  val uiState: StateFlow<LoginUiState> = mutableUiState

  fun onModeToggled() = mutableUiState.update {
    it.copy(mode = if (it.mode == LoginMode.Login) LoginMode.Register else LoginMode.Login, errorMessageRes = null)
  }

  fun onEmailChanged(value: String) = mutableUiState.update { it.copy(email = value, errorMessageRes = null) }
  fun onPasswordChanged(value: String) = mutableUiState.update { it.copy(password = value, errorMessageRes = null) }
  fun onDisplayNameChanged(value: String) = mutableUiState.update {
    it.copy(
      displayName = value,
      errorMessageRes = null
    )
  }

  fun onSubmit() {
    val state = mutableUiState.value
    val validationError = state.validate()
    if (validationError != null) {
      mutableUiState.update { it.copy(errorMessageRes = validationError) }
      return
    }

    viewModelScope.launch {
      mutableUiState.update { it.copy(isSubmitting = true, errorMessageRes = null) }
      val result = when (state.mode) {
        LoginMode.Login -> authController.login(state.email.trim(), state.password)
        LoginMode.Register -> authController.register(state.email.trim(), state.password, state.displayName.trim())
      }
      mutableUiState.update {
        if (result is AuthCallResult.Success) {
          it.copy(isSubmitting = false, success = true)
        } else {
          it.copy(isSubmitting = false, errorMessageRes = result.messageRes())
        }
      }
    }
  }
}

data class LoginUiState(
  val mode: LoginMode = LoginMode.Login,
  val email: String = "",
  val password: String = "",
  val displayName: String = "",
  val isSubmitting: Boolean = false,
  val success: Boolean = false,
  @param:StringRes val errorMessageRes: Int? = null,
)

private const val MinPasswordLength = 8

private fun LoginUiState.validate(): Int? = when {
  email.isBlank() || !email.contains('@') -> R.string.auth_error_invalid_email
  password.length < MinPasswordLength -> R.string.auth_error_short_password
  mode == LoginMode.Register && displayName.isBlank() -> R.string.auth_error_blank_display_name
  else -> null
}

@StringRes
private fun AuthCallResult.messageRes(): Int = when (this) {
  AuthCallResult.Success -> R.string.auth_error_network // unreachable: callers branch on Success first
  AuthCallResult.EmailTaken -> R.string.auth_error_email_taken
  AuthCallResult.InvalidCredentials -> R.string.auth_error_invalid_credentials
  AuthCallResult.GuestSessionNotFound -> R.string.auth_error_guest_session_not_found
  AuthCallResult.NetworkError -> R.string.auth_error_network
}
