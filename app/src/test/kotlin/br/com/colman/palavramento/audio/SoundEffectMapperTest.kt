// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.audio

import br.com.colman.palavramento.domain.submission.RejectionReason
import br.com.colman.palavramento.state.SubmissionFeedback
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SoundEffectMapperTest : FunSpec({

  test("An accepted word maps to the Accepted effect") {
    SubmissionFeedback.Accepted("CASA", 6, listOf(0, 1, 2, 3)).toSoundEffect() shouldBe SoundEffect.Accepted
  }

  test("AlreadyFound maps to its own, softer effect, not the generic rejection one") {
    SubmissionFeedback.Rejected(RejectionReason.AlreadyFound, listOf(0, 1)).toSoundEffect() shouldBe
      SoundEffect.AlreadyFound
  }

  test("Every other rejection reason maps to the generic Rejected effect") {
    val otherReasons = RejectionReason.entries.filterNot { it == RejectionReason.AlreadyFound }
    otherReasons.forEach { reason ->
      SubmissionFeedback.Rejected(reason, listOf(0, 1)).toSoundEffect() shouldBe SoundEffect.Rejected
    }
  }
})
