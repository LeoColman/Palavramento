// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import br.com.colman.palavramento.domain.protocol.AuthTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File

private fun tempDataStore(): DataStore<Preferences> {
  val file = File.createTempFile("palavramento-token-repository-test", ".preferences_pb")
  file.delete()
  file.deleteOnExit()
  return PreferenceDataStoreFactory.create { file }
}

private fun sampleTokens() = AuthTokens(
  playerId = "p1",
  displayName = "Convidado",
  isGuest = true,
  accessToken = "access-token",
  accessTokenExpiresAt = 1_000,
  refreshToken = "refresh-token",
)

class DataStoreTokenRepositoryTest : FunSpec({

  test("tokens is null before the first save") {
    runTest {
      val repository = DataStoreTokenRepository(tempDataStore())
      repository.tokens.first() shouldBe null
    }
  }

  test("save persists the tokens, and a later read returns exactly them") {
    runTest {
      val repository = DataStoreTokenRepository(tempDataStore())
      val tokens = sampleTokens()

      repository.save(tokens)

      repository.tokens.first() shouldBe tokens
    }
  }

  test("clear removes the saved tokens") {
    runTest {
      val repository = DataStoreTokenRepository(tempDataStore())
      repository.save(sampleTokens())

      repository.clear()

      repository.tokens.first() shouldBe null
    }
  }

  test("sessionExpired is false until it is set") {
    runTest {
      val repository = DataStoreTokenRepository(tempDataStore())
      repository.sessionExpired.first() shouldBe false
    }
  }

  test("setSessionExpired persists the flag both ways, independently of the tokens") {
    runTest {
      val repository = DataStoreTokenRepository(tempDataStore())

      repository.setSessionExpired(true)
      repository.clear()
      repository.sessionExpired.first() shouldBe true

      repository.setSessionExpired(false)
      repository.sessionExpired.first() shouldBe false
    }
  }
})
