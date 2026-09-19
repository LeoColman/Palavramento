// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.auth

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith

class PasswordHasherTest : FunSpec({
  test("A hash verifies against the password that produced it") {
    val hash = PasswordHasher.hash("correct horse battery staple")

    PasswordHasher.verify("correct horse battery staple", hash) shouldBe true
  }

  test("A hash does not verify against any other password") {
    val hash = PasswordHasher.hash("correct horse battery staple")

    PasswordHasher.verify("correct horse battery stapler", hash) shouldBe false
    PasswordHasher.verify("", hash) shouldBe false
  }

  test("The hash is bcrypt at cost 12, and never the password itself") {
    val hash = PasswordHasher.hash("senha")

    // bcrypt's own prefix: version 2a, cost 12. Lowering the cost would be a silent security change.
    hash shouldStartWith "\$2a\$12\$"
    hash shouldNotBe "senha"
  }

  test("The same password hashes to a different string every time, and both still verify") {
    val first = PasswordHasher.hash("senha")
    val second = PasswordHasher.hash("senha")

    first shouldNotBe second
    PasswordHasher.verify("senha", first) shouldBe true
    PasswordHasher.verify("senha", second) shouldBe true
  }
})
