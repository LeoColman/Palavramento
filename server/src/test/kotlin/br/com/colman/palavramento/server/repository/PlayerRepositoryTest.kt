// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.repository

import br.com.colman.palavramento.server.db.tables.PlayersTable
import br.com.colman.palavramento.server.testsupport.testDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.jdbc.insert
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

private fun guestRow(id: String = UUID.randomUUID().toString(), displayName: String = "Guest $id") = PlayerRow(
  id = id,
  displayName = displayName,
  isGuest = true,
  authProvider = GuestAuthProvider,
  email = null,
  passwordHash = null,
  createdAt = Instant.parse("2026-01-01T00:00:00Z"),
)

private fun registeredRow(
  id: String = UUID.randomUUID().toString(),
  email: String = "player-$id@example.com",
) = PlayerRow(
  id = id,
  displayName = "Registered $id",
  isGuest = false,
  authProvider = PasswordAuthProvider,
  email = email,
  passwordHash = "hash-of-$id",
  createdAt = Instant.parse("2026-01-02T00:00:00Z"),
)

/** CRUD over `players` (dossier §7/§8), including the guest-promotion path. */
class PlayerRepositoryTest : FunSpec({
  val database = testDatabase()

  test("insert stores a guest row that findById returns back, field for field") {
    val repository = PlayerRepository(database)
    val row = guestRow()

    repository.insert(row)

    val found = repository.findById(row.id)
    found.shouldNotBeNull()
    found.id shouldBe row.id
    found.displayName shouldBe row.displayName
    found.isGuest shouldBe true
    found.authProvider shouldBe GuestAuthProvider
    found.email.shouldBeNull()
    found.passwordHash.shouldBeNull()
    found.createdAt shouldBe row.createdAt
  }

  test("insert stores a registered row with its email and password hash, isGuest false") {
    val repository = PlayerRepository(database)
    val row = registeredRow()

    repository.insert(row)

    val found = repository.findById(row.id)
    found?.isGuest shouldBe false
    found?.authProvider shouldBe PasswordAuthProvider
    found?.email shouldBe row.email
    found?.passwordHash shouldBe row.passwordHash
  }

  test("findById returns null for an id that was never stored") {
    val repository = PlayerRepository(database)

    repository.findById(UUID.randomUUID().toString()).shouldBeNull()
  }

  test("findByIds returns an empty map for an empty collection, without querying for it") {
    val repository = PlayerRepository(database)

    repository.findByIds(emptyList()).shouldBeEmpty()
  }

  test("findByIds returns only the matching rows, keyed by id, and ignores ids that don't exist") {
    val repository = PlayerRepository(database)
    val first = guestRow()
    val second = guestRow()
    val neverStored = UUID.randomUUID().toString()
    repository.insert(first)
    repository.insert(second)

    val found = repository.findByIds(listOf(first.id, second.id, neverStored))

    found shouldHaveSize 2
    found[first.id]?.displayName shouldBe first.displayName
    found[second.id]?.displayName shouldBe second.displayName
    found.containsKey(neverStored) shouldBe false
  }

  test("findByEmail finds the matching row, and returns null when nothing matches") {
    val repository = PlayerRepository(database)
    val row = registeredRow()
    repository.insert(row)

    repository.findByEmail(row.email!!)?.id shouldBe row.id
    repository.findByEmail("nobody-${UUID.randomUUID()}@example.com").shouldBeNull()
  }

  test("promote flips a guest into a registered account, overwriting the given fields and keeping id/createdAt") {
    val repository = PlayerRepository(database)
    val guest = guestRow()
    repository.insert(guest)

    val promotedEmail = "promoted-${UUID.randomUUID()}@example.com"
    repository.promote(guest.id, promotedEmail, "new-hash", "New Name")

    val promoted = repository.findById(guest.id)
    promoted.shouldNotBeNull()
    promoted.id shouldBe guest.id
    promoted.isGuest shouldBe false
    promoted.authProvider shouldBe PasswordAuthProvider
    promoted.email shouldBe promotedEmail
    promoted.passwordHash shouldBe "new-hash"
    promoted.displayName shouldBe "New Name"
    promoted.createdAt shouldBe guest.createdAt
  }

  test("updateDisplayName changes only the display name") {
    val repository = PlayerRepository(database)
    val row = registeredRow()
    repository.insert(row)

    repository.updateDisplayName(row.id, "A Brand New Name")

    val updated = repository.findById(row.id)
    updated?.displayName shouldBe "A Brand New Name"
    updated?.email shouldBe row.email
    updated?.passwordHash shouldBe row.passwordHash
    updated?.isGuest shouldBe row.isGuest
  }

  test("delete removes the row: a repeated find then returns null") {
    val repository = PlayerRepository(database)
    val row = guestRow()
    repository.insert(row)
    repository.findById(row.id).shouldNotBeNull()

    repository.delete(row.id)

    repository.findById(row.id).shouldBeNull()
  }

  test("transaction runs the block exactly once, returns its own result, and commits its writes") {
    val repository = PlayerRepository(database)
    val row = guestRow()
    var runCount = 0

    val result = repository.transaction {
      runCount++
      // A raw write through the open JdbcTransaction, the way callers compose multi-table writes.
      PlayersTable.insert {
        it[id] = row.id
        it[displayName] = row.displayName
        it[isGuest] = row.isGuest
        it[authProvider] = row.authProvider
        it[email] = row.email
        it[passwordHash] = row.passwordHash
        it[createdAt] = row.createdAt.atOffset(ZoneOffset.UTC)
      }
      "block-result"
    }

    result shouldBe "block-result"
    runCount shouldBe 1
    repository.findById(row.id).shouldNotBeNull()
  }
})
