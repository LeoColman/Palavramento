// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.auth

import at.favre.lib.crypto.bcrypt.BCrypt

/** Wraps bcrypt for password storage (dossier §8: "senhas com bcrypt"). */
object PasswordHasher {
  private const val Cost = 12

  fun hash(password: String): String = BCrypt.withDefaults().hashToString(Cost, password.toCharArray())

  fun verify(password: String, hash: String): Boolean = BCrypt.verifyer().verify(password.toCharArray(), hash).verified
}
