// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.data

import br.com.colman.palavramento.domain.protocol.AuthTokens
import kotlinx.coroutines.flow.Flow

/**
 * Persists the current [AuthTokens] (dossier 8): a guest identity by default, created on first
 * launch via `POST /auth/guest`. Deliberately minimal for phase 4 - just get/save/clear - so phase
 * 5's login/promotion/refresh flow can extend it without reshaping what already depends on it.
 */
interface TokenRepository {

  /** The stored tokens, or null before the first `guestAuth` call. Emits again on every [save]. */
  val tokens: Flow<AuthTokens?>

  suspend fun save(tokens: AuthTokens)

  suspend fun clear()
}
