// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.ui.history

import br.com.colman.palavramento.data.FakeHistoryRepository
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.protocol.RoundHistoryEntry
import br.com.colman.palavramento.domain.stats.RoundStats
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.time.Duration.Companion.seconds

private fun sampleRound() = RoundHistoryEntry(
  roundId = "round-1",
  startsAt = 1_000L,
  board = List(16) { Tile("A", 1) },
  mutator = Mutator.NoMutator,
  themeTitle = "Grade padrao",
  themeSubtitle = "15 palavras comuns",
  maxScore = 4193,
  maxWords = 272,
  stats = RoundStats(73, 6, 12.2, 3.5, 0, 12.2, 14),
  rank = 1,
  totalPlayers = 1,
  words = emptyList(),
)

/** [HistoryViewModel] reads straight from the cache (task brief 3: "Historico visivel offline"). */
class HistoryViewModelTest : FunSpec({

  beforeTest { Dispatchers.setMain(UnconfinedTestDispatcher()) }
  afterTest { Dispatchers.resetMain() }

  test("rounds starts empty when the cache has nothing") {
    val viewModel = HistoryViewModel(FakeHistoryRepository())
    viewModel.rounds.value.shouldBeEmpty()
  }

  test("rounds reflects whatever is already cached, with no network call needed") {
    val repository = FakeHistoryRepository()
    val round = sampleRound()
    val viewModel = HistoryViewModel(repository)

    repository.upsertAll(listOf(round))

    eventually(2.seconds) { viewModel.rounds.value shouldBe listOf(round) }
  }
})
