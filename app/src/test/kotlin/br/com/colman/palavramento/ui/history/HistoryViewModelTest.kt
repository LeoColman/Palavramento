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
// 10 s, not 2: `eventually` returns the moment the condition holds, so this bound costs nothing on
// a healthy run and only decides how long to wait before giving up. Two seconds was enough running
// the suite alone and not enough under PIT, which re-runs it while `org.gradle.parallel` has another
// module's mutation run on the same machine. One of these timing out there fails the coverage phase,
// and PIT needs a green suite to start at all, so the whole :app gate died on a flake.
private val EventuallyTimeout = 10.seconds

class HistoryViewModelTest : FunSpec({

  // Installed for the whole spec and deliberately never reset: a view model started by one test can
  // still be cancelling while the next one runs (RoomViewModel's session loop never ends on its own),
  // and resetting Main out from under it dispatches that cancellation into a Main dispatcher that no
  // longer exists, which on the JVM fails as "Looper not mocked". There is no real Main to restore.
  beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }

  test("rounds starts empty when the cache has nothing") {
    val viewModel = HistoryViewModel(FakeHistoryRepository())
    viewModel.rounds.value.shouldBeEmpty()
  }

  test("rounds reflects whatever is already cached, with no network call needed") {
    val repository = FakeHistoryRepository()
    val round = sampleRound()
    val viewModel = HistoryViewModel(repository)

    repository.upsertAll(listOf(round))

    eventually(EventuallyTimeout) { viewModel.rounds.value shouldBe listOf(round) }
  }
})
