// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Generates opaque refresh tokens and hashes them for storage (dossier §8: only the hash is kept). */
object TokenHasher {
  private const val RawTokenBytes = 32
  private const val ByteMask = 0xFF
  private val random = SecureRandom()
  private val encoder = Base64.getUrlEncoder().withoutPadding()

  /** A fresh random raw token, never persisted as-is. */
  fun newRawToken(): String {
    val bytes = ByteArray(RawTokenBytes)
    random.nextBytes(bytes)
    return encoder.encodeToString(bytes)
  }

  /** SHA-256 of [rawToken], hex-encoded: what actually gets stored and compared. */
  fun hash(rawToken: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { "%02x".format(it.toInt() and ByteMask) }
  }
}
