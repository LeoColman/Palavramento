// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File

private fun tempDataStore(): DataStore<Preferences> {
  val file = File.createTempFile("palavramento-settings-repository-test", ".preferences_pb")
  file.delete()
  file.deleteOnExit()
  return PreferenceDataStoreFactory.create { file }
}

class DataStoreSettingsRepositoryTest : FunSpec({

  test("hapticsEnabled defaults to true before any save") {
    runTest {
      val repository = DataStoreSettingsRepository(tempDataStore())
      repository.hapticsEnabled.first() shouldBe true
    }
  }

  test("setHapticsEnabled(false) persists, and a later read returns exactly that") {
    runTest {
      val repository = DataStoreSettingsRepository(tempDataStore())

      repository.setHapticsEnabled(false)

      repository.hapticsEnabled.first() shouldBe false
    }
  }

  test("setHapticsEnabled can be toggled back on") {
    runTest {
      val repository = DataStoreSettingsRepository(tempDataStore())

      repository.setHapticsEnabled(false)
      repository.setHapticsEnabled(true)

      repository.hapticsEnabled.first() shouldBe true
    }
  }

  test("musicEnabled and effectsEnabled also default to true before any save") {
    runTest {
      val repository = DataStoreSettingsRepository(tempDataStore())
      repository.musicEnabled.first() shouldBe true
      repository.effectsEnabled.first() shouldBe true
    }
  }

  test("setMusicEnabled(false) persists independently of the other two toggles") {
    runTest {
      val repository = DataStoreSettingsRepository(tempDataStore())

      repository.setMusicEnabled(false)

      repository.musicEnabled.first() shouldBe false
      repository.hapticsEnabled.first() shouldBe true
      repository.effectsEnabled.first() shouldBe true
    }
  }

  test("setEffectsEnabled(false) persists independently of the other two toggles") {
    runTest {
      val repository = DataStoreSettingsRepository(tempDataStore())

      repository.setEffectsEnabled(false)

      repository.effectsEnabled.first() shouldBe false
      repository.hapticsEnabled.first() shouldBe true
      repository.musicEnabled.first() shouldBe true
    }
  }
})
