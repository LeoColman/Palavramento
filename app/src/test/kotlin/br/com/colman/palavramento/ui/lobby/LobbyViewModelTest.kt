// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.lobby

import android.util.Log
import br.com.colman.palavramento.data.AuthController
import br.com.colman.palavramento.data.FakeHistoryRepository
import br.com.colman.palavramento.data.FakeProfileRepository
import br.com.colman.palavramento.data.FakeTokenRepository
import br.com.colman.palavramento.data.SyncService
import br.com.colman.palavramento.domain.protocol.AuthTokens
import br.com.colman.palavramento.domain.protocol.PalavramentoJson
import br.com.colman.palavramento.domain.protocol.PlayerProfile
import br.com.colman.palavramento.domain.protocol.RoundHistoryEntry
import br.com.colman.palavramento.network.KtorRestApi
import br.com.colman.palavramento.network.RestApi
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.builtins.ListSerializer
import kotlin.time.Duration.Companion.seconds

private fun restApiOf(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): RestApi {
  val client = HttpClient(MockEngine(handler)) {
    expectSuccess = true
    install(ContentNegotiation) { json(PalavramentoJson) }
  }
  return KtorRestApi(client, "http://test")
}

private fun MockRequestHandleScope.jsonOk(content: String) =
  respond(content, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

private fun guestTokens() = AuthTokens(
  "guest-1",
  "Convidado ABCD",
  isGuest = true,
  "access",
  999_999_999_999L,
  "refresh"
)

private fun emptyRoundsResponse(request: HttpRequestData, scope: MockRequestHandleScope): HttpResponseData? =
  if (request.url.encodedPath == "/players/me/rounds") {
    scope.jsonOk(
      PalavramentoJson.encodeToString(
        ListSerializer(RoundHistoryEntry.serializer()),
        emptyList()
      )
    )
  } else {
    null
  }

private val EventuallyTimeout = 2.seconds

/**
 * [LobbyViewModel] bootstrap/sync/error/retry state (task brief 2/4, orchestrator finding: a
 * silent bootstrap failure must surface as [LobbyUiState.loadError], not an endless "Jogar" spinner).
 */
class LobbyViewModelTest : FunSpec({

  // android.util.Log is unmocked on the plain JVM (no Robolectric in this project): a bare Log.w
  // call throws "not mocked" and would silently kill LobbyViewModel's launch{} coroutine before it
  // ever reaches the loadError update, which is exactly the failure path these tests exercise.
  // Installed for the whole spec and deliberately never reset: a view model started by one test can
  // still be cancelling while the next one runs (RoomViewModel's session loop never ends on its own),
  // and resetting Main out from under it dispatches that cancellation into a Main dispatcher that no
  // longer exists, which on the JVM fails as "Looper not mocked". There is no real Main to restore.
  beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
  beforeTest {
    mockkStatic(Log::class)
    every { Log.w(any(), any<String>()) } returns 0
  }
  afterTest { unmockkStatic(Log::class) }

  test("bootstraps a guest, syncs, and clears isLoading with no error") {
    val profile = PlayerProfile(
      "guest-1",
      "Convidado ABCD",
      isGuest = true,
      level = 1,
      totalXp = 0,
      xpForNextLevel = 100
    )
    val restApi = restApiOf { request ->
      emptyRoundsResponse(request, this) ?: when (request.url.encodedPath) {
        "/auth/guest" -> jsonOk(PalavramentoJson.encodeToString(AuthTokens.serializer(), guestTokens()))
        "/players/me" -> jsonOk(PalavramentoJson.encodeToString(PlayerProfile.serializer(), profile))
        else -> error("Unexpected request: ${request.url}")
      }
    }
    val tokenRepository = FakeTokenRepository()
    val profileRepository = FakeProfileRepository()
    val historyRepository = FakeHistoryRepository()
    val authController = AuthController(restApi, tokenRepository, profileRepository, historyRepository)
    val syncService = SyncService(restApi, authController, profileRepository, historyRepository)
    val viewModel = LobbyViewModel(authController, syncService, profileRepository)

    eventually(EventuallyTimeout) { viewModel.uiState.value.isLoading shouldBe false }
    viewModel.uiState.value.loadError shouldBe false
    viewModel.uiState.value.isGuest shouldBe true
    eventually(EventuallyTimeout) { viewModel.uiState.value.profile shouldBe profile }
  }

  test("a guest bootstrap failure surfaces loadError instead of spinning forever") {
    val restApi = restApiOf { throw java.io.IOException("offline") }
    val profileRepository = FakeProfileRepository()
    val historyRepository = FakeHistoryRepository()
    val authController = AuthController(restApi, FakeTokenRepository(), profileRepository, historyRepository)
    val syncService = SyncService(restApi, authController, profileRepository, historyRepository)
    val viewModel = LobbyViewModel(authController, syncService, profileRepository)

    eventually(EventuallyTimeout) { viewModel.uiState.value.isLoading shouldBe false }
    viewModel.uiState.value.loadError shouldBe true
  }

  test("refresh() recovers after the network comes back") {
    var online = false
    val profile = PlayerProfile(
      "guest-1",
      "Convidado ABCD",
      isGuest = true,
      level = 1,
      totalXp = 0,
      xpForNextLevel = 100
    )
    val restApi = restApiOf { request ->
      if (!online) throw java.io.IOException("offline")
      emptyRoundsResponse(request, this) ?: when (request.url.encodedPath) {
        "/auth/guest" -> jsonOk(PalavramentoJson.encodeToString(AuthTokens.serializer(), guestTokens()))
        "/players/me" -> jsonOk(PalavramentoJson.encodeToString(PlayerProfile.serializer(), profile))
        else -> error("Unexpected request: ${request.url}")
      }
    }
    val tokenRepository = FakeTokenRepository()
    val profileRepository = FakeProfileRepository()
    val historyRepository = FakeHistoryRepository()
    val authController = AuthController(restApi, tokenRepository, profileRepository, historyRepository)
    val syncService = SyncService(restApi, authController, profileRepository, historyRepository)
    val viewModel = LobbyViewModel(authController, syncService, profileRepository)

    eventually(EventuallyTimeout) { viewModel.uiState.value.loadError shouldBe true }

    online = true
    viewModel.refresh()

    eventually(EventuallyTimeout) { viewModel.uiState.value.loadError shouldBe false }
    // profile arrives through a separate collector off the same cache Flow (see LobbyViewModel's
    // init), so it can trail the isLoading/loadError update by a beat; eventually absorbs that too.
    eventually(EventuallyTimeout) { viewModel.uiState.value.profile shouldBe profile }
  }

  test("onLogoutClicked clears the session and bootstraps a fresh guest") {
    val registered = AuthTokens("player-1", "Ana", isGuest = false, "old-access", 999_999_999_999L, "old-refresh")
    val freshGuest = guestTokens()
    val registeredProfile = PlayerProfile(registered.playerId, registered.displayName, false, 3, 900, 1600)
    val guestProfile = PlayerProfile(freshGuest.playerId, freshGuest.displayName, true, 1, 0, 100)
    val restApi = restApiOf { request ->
      emptyRoundsResponse(request, this) ?: when (request.url.encodedPath) {
        "/auth/guest" -> jsonOk(PalavramentoJson.encodeToString(AuthTokens.serializer(), freshGuest))
        "/players/me" -> {
          val isRegistered = request.headers[HttpHeaders.Authorization] == "Bearer old-access"
          val profile = if (isRegistered) registeredProfile else guestProfile
          jsonOk(PalavramentoJson.encodeToString(PlayerProfile.serializer(), profile))
        }

        else -> error("Unexpected request: ${request.url}")
      }
    }
    val tokenRepository = FakeTokenRepository(registered)
    val profileRepository = FakeProfileRepository()
    val historyRepository = FakeHistoryRepository()
    val authController = AuthController(restApi, tokenRepository, profileRepository, historyRepository)
    val syncService = SyncService(restApi, authController, profileRepository, historyRepository)
    val viewModel = LobbyViewModel(authController, syncService, profileRepository)

    eventually(EventuallyTimeout) { viewModel.uiState.value.isGuest shouldBe false }

    viewModel.onLogoutClicked()

    eventually(EventuallyTimeout) { viewModel.uiState.value.isGuest shouldBe true }
  }

  test("a registered player whose session was rejected lands on a new guest with the expired notice") {
    val registered = AuthTokens("player-1", "Ana", isGuest = false, "old-access", 0L, "old-refresh")
    val freshGuest = guestTokens()
    val guestProfile = PlayerProfile(freshGuest.playerId, freshGuest.displayName, true, 1, 0, 100)
    val restApi = restApiOf { request ->
      emptyRoundsResponse(request, this) ?: when (request.url.encodedPath) {
        "/auth/refresh" -> respond("", HttpStatusCode.Unauthorized)
        "/auth/guest" -> jsonOk(PalavramentoJson.encodeToString(AuthTokens.serializer(), freshGuest))
        "/players/me" -> jsonOk(PalavramentoJson.encodeToString(PlayerProfile.serializer(), guestProfile))
        else -> error("Unexpected request: ${request.url}")
      }
    }
    val profileRepository = FakeProfileRepository()
    val historyRepository = FakeHistoryRepository()
    // A fixed clock: the fixtures' far-off expiry (999_999_999_999 ms) is already past by the real one.
    val authController =
      AuthController(restApi, FakeTokenRepository(registered), profileRepository, historyRepository, nowMs = { 0L })
    val syncService = SyncService(restApi, authController, profileRepository, historyRepository)
    val viewModel = LobbyViewModel(authController, syncService, profileRepository)

    eventually(EventuallyTimeout) { viewModel.uiState.value.sessionExpired shouldBe true }
    eventually(EventuallyTimeout) { viewModel.uiState.value.isLoading shouldBe false }
    viewModel.uiState.value.isGuest shouldBe true
    viewModel.uiState.value.loadError shouldBe false
    eventually(EventuallyTimeout) { viewModel.uiState.value.profile shouldBe guestProfile }

    viewModel.onSessionExpiredDismissed()

    eventually(EventuallyTimeout) { viewModel.uiState.value.sessionExpired shouldBe false }
  }
})
