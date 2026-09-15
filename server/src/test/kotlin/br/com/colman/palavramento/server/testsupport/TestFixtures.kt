// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.testsupport

import br.com.colman.palavramento.server.repository.GuestAuthProvider
import br.com.colman.palavramento.server.repository.PlayerRepository
import br.com.colman.palavramento.server.repository.PlayerRow
import java.time.Instant
import java.util.UUID

/**
 * Inserts and returns a fresh guest [PlayerRow], for tests that drive
 * [br.com.colman.palavramento.server.round.RoomScheduler] directly.
 */
suspend fun PlayerRepository.insertGuest(
  displayName: String = "Guest ${UUID.randomUUID().toString().take(4)}"
): PlayerRow {
  val row = PlayerRow(
    id = UUID.randomUUID().toString(),
    displayName = displayName,
    isGuest = true,
    authProvider = GuestAuthProvider,
    email = null,
    passwordHash = null,
    createdAt = Instant.now(),
  )
  insert(row)
  return row
}
