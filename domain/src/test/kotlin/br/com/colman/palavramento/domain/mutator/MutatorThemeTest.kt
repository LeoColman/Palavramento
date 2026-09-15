// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.mutator

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MutatorThemeTest : FunSpec({
  test("ValuableLetter title matches the dossier's example") {
    MutatorTheme.title(Mutator.ValuableLetter('L', 10)) shouldBe "L de alto valor"
  }

  test("Every mutator has a non-blank title") {
    listOf(
      Mutator.NoMutator,
      Mutator.ValuableLetter('L', 10),
      Mutator.ForbiddenLetter('E'),
      Mutator.MinimumLength(5),
    ).forEach { mutator -> MutatorTheme.title(mutator).isNotBlank() shouldBe true }
  }

  test("Subtitle states the generation restriction literally, dossier example") {
    MutatorTheme.subtitle(commonMin = 19) shouldBe "19 palavras comuns"
  }
})
