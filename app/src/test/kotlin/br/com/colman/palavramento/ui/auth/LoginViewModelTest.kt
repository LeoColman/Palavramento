// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.auth

import br.com.colman.palavramento.R
import br.com.colman.palavramento.data.AuthController
import br.com.colman.palavramento.data.FakeHistoryRepository
import br.com.colman.palavramento.data.FakeProfileRepository
import br.com.colman.palavramento.data.FakeTokenRepository
import br.com.colman.palavramento.domain.protocol.AuthTokens
import br.com.colman.palavramento.domain.protocol.PalavramentoJson
import br.com.colman.palavramento.network.KtorRestApi
import br.com.colman.palavramento.network.RestApi
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlin.time.Duration.Companion.seconds

private fun restApiOf(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): RestApi {
  val client = HttpClient(MockEngine(handler)) {
    expectSuccess = true
    install(ContentNegotiation) { json(PalavramentoJson) }
  }
  return KtorRestApi(client, "http://test")
}

private fun controllerOf(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
  AuthController(restApiOf(handler), FakeTokenRepository(), FakeProfileRepository(), FakeHistoryRepository())

private fun MockRequestHandleScope.jsonOkTokens() = respond(
  content = PalavramentoJson.encodeToString(
    AuthTokens.serializer(),
    AuthTokens("p1", "Ana", isGuest = false, "access", 999_999_999_999L, "refresh"),
  ),
  status = HttpStatusCode.OK,
  headers = headersOf(HttpHeaders.ContentType, "application/json"),
)

/**
 * [LoginViewModel] state transitions (task brief: "login/registration ViewModel state").
 * `viewModelScope` dispatches on `Dispatchers.Main`, so every test needs a Main dispatcher
 * substitute; [UnconfinedTestDispatcher] runs `onSubmit`'s launched coroutine to completion
 * synchronously (nothing here does a real `delay`), so assertions can read `uiState` right after
 * calling a view model method, with no extra `advanceUntilIdle()` bookkeeping.
 */
class LoginViewModelTest : FunSpec({

  // Installed for the whole spec and deliberately never reset: a view model started by one test can
  // still be cancelling while the next one runs (RoomViewModel's session loop never ends on its own),
  // and resetting Main out from under it dispatches that cancellation into a Main dispatcher that no
  // longer exists, which on the JVM fails as "Looper not mocked". There is no real Main to restore.
  beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }

  test("starts in Login mode with empty fields and no error") {
    val viewModel = LoginViewModel(controllerOf { error("no network expected") })
    val state = viewModel.uiState.value

    state.mode shouldBe LoginMode.Login
    state.email shouldBe ""
    state.password shouldBe ""
    state.errorMessageRes shouldBe null
    state.success shouldBe false
  }

  test("onModeToggled switches between Login and Register and clears any error") {
    val viewModel = LoginViewModel(controllerOf { error("no network expected") })

    viewModel.onModeToggled()
    viewModel.uiState.value.mode shouldBe LoginMode.Register

    viewModel.onModeToggled()
    viewModel.uiState.value.mode shouldBe LoginMode.Login
  }

  test("onSubmit with a blank email fails client-side validation without calling the network") {
    val viewModel = LoginViewModel(controllerOf { error("no network expected: validation must fail first") })
    viewModel.onPasswordChanged("correct horse battery staple")

    viewModel.onSubmit()

    viewModel.uiState.value.errorMessageRes shouldBe R.string.auth_error_invalid_email
    viewModel.uiState.value.success shouldBe false
  }

  test("onSubmit with a short password fails client-side validation") {
    val viewModel = LoginViewModel(controllerOf { error("no network expected") })
    viewModel.onEmailChanged("ana@example.com")
    viewModel.onPasswordChanged("short")

    viewModel.onSubmit()

    viewModel.uiState.value.errorMessageRes shouldBe R.string.auth_error_short_password
  }

  test("Register mode with a blank display name fails client-side validation") {
    val viewModel = LoginViewModel(controllerOf { error("no network expected") })
    viewModel.onModeToggled()
    viewModel.onEmailChanged("ana@example.com")
    viewModel.onPasswordChanged("correct horse battery staple")

    viewModel.onSubmit()

    viewModel.uiState.value.errorMessageRes shouldBe R.string.auth_error_blank_display_name
  }

  // These four exercise onSubmit()'s viewModelScope.launch coroutine, which is not structurally
  // part of the test's own coroutine (a plain runTest/advanceUntilIdle has no way to await it: it
  // is a fire-and-forget launch on a separate, ViewModel-owned scope, same reason ADR 0006 gives
  // for why LobbyViewModel/RoomViewModel have no JVM tests of their own). eventually polls uiState
  // for up to a couple of seconds instead, which is deterministic enough for a MockEngine response
  // that never genuinely delays.

  test("a successful login sets success, ready for the screen to navigate back") {
    val viewModel = LoginViewModel(controllerOf { jsonOkTokens() })
    viewModel.onEmailChanged("ana@example.com")
    viewModel.onPasswordChanged("correct horse battery staple")

    viewModel.onSubmit()

    eventually(EventuallyTimeout) { viewModel.uiState.value.success shouldBe true }
    viewModel.uiState.value.isSubmitting shouldBe false
  }

  test("invalid credentials (401) surface as a pt-BR error, not success") {
    val viewModel = LoginViewModel(controllerOf { respondError(HttpStatusCode.Unauthorized) })
    viewModel.onEmailChanged("ana@example.com")
    viewModel.onPasswordChanged("wrong-password-1")

    viewModel.onSubmit()

    eventually(
      EventuallyTimeout
    ) { viewModel.uiState.value.errorMessageRes shouldBe R.string.auth_error_invalid_credentials }
    viewModel.uiState.value.success shouldBe false
  }

  test("registering with a taken e-mail (409) surfaces EmailTaken") {
    val viewModel = LoginViewModel(controllerOf { respondError(HttpStatusCode.Conflict) })
    viewModel.onModeToggled()
    viewModel.onEmailChanged("taken@example.com")
    viewModel.onPasswordChanged("correct horse battery staple")
    viewModel.onDisplayNameChanged("Ana")

    viewModel.onSubmit()

    eventually(EventuallyTimeout) { viewModel.uiState.value.errorMessageRes shouldBe R.string.auth_error_email_taken }
  }

  test("a network failure surfaces a generic pt-BR error") {
    val viewModel = LoginViewModel(controllerOf { throw java.io.IOException("offline") })
    viewModel.onEmailChanged("ana@example.com")
    viewModel.onPasswordChanged("correct horse battery staple")

    viewModel.onSubmit()

    eventually(EventuallyTimeout) { viewModel.uiState.value.errorMessageRes shouldBe R.string.auth_error_network }
  }
})

private val EventuallyTimeout = 2.seconds
