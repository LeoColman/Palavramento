// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.data

import br.com.colman.palavramento.domain.protocol.AuthTokens
import kotlinx.coroutines.flow.Flow

/**
 * Persists the current [AuthTokens] (dossier 8): a guest identity by default, created on first
 * launch via `POST /auth/guest`. Also keeps whether a registered player's session was rejected, so
 * the lobby can still ask them to log in again after the app restarts.
 */
interface TokenRepository {

  /** The stored tokens, or null before the first `guestAuth` call. Emits again on every [save]. */
  val tokens: Flow<AuthTokens?>

  suspend fun save(tokens: AuthTokens)

  suspend fun clear()

  /** True once a registered player's session was rejected, until [setSessionExpired] resets it. */
  val sessionExpired: Flow<Boolean>

  suspend fun setSessionExpired(expired: Boolean)
}
