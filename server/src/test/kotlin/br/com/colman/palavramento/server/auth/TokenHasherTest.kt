// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldMatch

class TokenHasherTest : FunSpec({
  test("hash is the hex SHA-256 of the raw token") {
    // Checked against an independent implementation: `printf 'abc' | sha256sum`.
    TokenHasher.hash("abc") shouldBe "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
  }

  test("hash is 64 lowercase hex characters, zero-padded bytes included") {
    // "1234" hashes to a digest whose first byte is 0x03: a byte formatted without its leading zero
    // would come back 63 characters long.
    val digest = TokenHasher.hash("1234")
    digest shouldHaveLength 64
    digest shouldMatch Regex("[0-9a-f]{64}")
    digest shouldBe "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4"
  }

  test("Different tokens hash differently, the same token always to the same value") {
    TokenHasher.hash("token-a") shouldNotBe TokenHasher.hash("token-b")
    TokenHasher.hash("token-a") shouldBe TokenHasher.hash("token-a")
  }

  test("The raw token is URL-safe base64 without padding, and never repeats") {
    val tokens = List(50) { TokenHasher.newRawToken() }

    tokens.toSet() shouldHaveSize 50
    tokens.forEach { token ->
      token shouldMatch Regex("[A-Za-z0-9_-]+")
      // 32 random bytes, base64 without padding: ceil(32 * 4 / 3) = 43 characters.
      token shouldHaveLength 43
    }
  }

  test("A raw token is never stored as-is: its hash does not contain it") {
    val raw = TokenHasher.newRawToken()

    TokenHasher.hash(raw) shouldNotBe raw
  }
})
