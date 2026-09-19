// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.domain.mutator

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MutatorThemeTest : FunSpec({
  test("ValuableLetter title matches the dossier's example") {
    MutatorTheme.title(Mutator.ValuableLetter('L', 10)) shouldBe "L de alto valor"
  }

  test("Digraphs title is fixed regardless of count") {
    MutatorTheme.title(Mutator.Digraphs(2)) shouldBe "Dígrafos"
    MutatorTheme.title(Mutator.Digraphs(4)) shouldBe "Dígrafos"
  }

  test("LetterInCorners title names the letter in upper case") {
    MutatorTheme.title(Mutator.LetterInCorners('O')) shouldBe "O nos cantos"
  }

  test("OneOrOther title names both letters, owner's own example (ADR 0015)") {
    MutatorTheme.title(Mutator.OneOrOther('A', 'F')) shouldBe "Uma ou outra: A/F"
  }

  test("Every mutator has a non-blank title") {
    listOf(
      Mutator.NoMutator,
      Mutator.ValuableLetter('L', 10),
      Mutator.Digraphs(3),
      Mutator.LetterInCorners('O'),
      Mutator.OneOrOther('A', 'F'),
      Mutator.Unknown,
    ).forEach { mutator -> MutatorTheme.title(mutator).isNotBlank() shouldBe true }
  }

  test("A rule this build does not know still has a title to show (ADR 0018)") {
    MutatorTheme.title(Mutator.Unknown) shouldBe "Grade especial"
  }

  test("Subtitle states the generation restriction literally, dossier example") {
    MutatorTheme.subtitle(commonMin = 19) shouldBe "19 palavras comuns"
  }
})
