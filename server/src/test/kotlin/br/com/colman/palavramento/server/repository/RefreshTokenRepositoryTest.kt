// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.repository

import br.com.colman.palavramento.server.testsupport.insertGuest
import br.com.colman.palavramento.server.testsupport.testDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

private fun freshTokenRow(playerId: String, hash: String = UUID.randomUUID().toString()) = RefreshTokenRow(
  id = UUID.randomUUID().toString(),
  playerId = playerId,
  tokenHash = hash,
  createdAt = Instant.parse("2026-01-01T00:00:00Z"),
  expiresAt = Instant.parse("2026-02-01T00:00:00Z"),
  revokedAt = null,
  replacedByHash = null,
)

/** CRUD and rotation bookkeeping over `refresh_tokens` (dossier §8, ADR 0007). */
class RefreshTokenRepositoryTest : FunSpec({
  val database = testDatabase()

  test("insert stores a row that findByHash returns back, field for field") {
    val repository = RefreshTokenRepository(database)
    val playerRepository = PlayerRepository(database)
    val player = playerRepository.insertGuest()
    val row = freshTokenRow(player.id)

    repository.insert(row)

    val found = repository.findByHash(row.tokenHash)
    found.shouldNotBeNull()
    found.id shouldBe row.id
    found.playerId shouldBe player.id
    found.tokenHash shouldBe row.tokenHash
    found.createdAt shouldBe row.createdAt
    found.expiresAt shouldBe row.expiresAt
    found.revokedAt.shouldBeNull()
    found.replacedByHash.shouldBeNull()
  }

  test("findByHash returns null for a hash that was never stored") {
    val repository = RefreshTokenRepository(database)

    repository.findByHash("never-stored-hash").shouldBeNull()
  }

  test("revoke marks only the matching token, setting revokedAt and replacedByHash exactly") {
    val repository = RefreshTokenRepository(database)
    val playerRepository = PlayerRepository(database)
    val player = playerRepository.insertGuest()
    val target = freshTokenRow(player.id)
    val untouched = freshTokenRow(player.id)
    repository.insert(target)
    repository.insert(untouched)

    val revokedAt = Instant.parse("2026-01-05T12:00:00Z")
    repository.revoke(target.tokenHash, "replacement-hash", revokedAt)

    val revoked = repository.findByHash(target.tokenHash)
    revoked?.revokedAt shouldBe revokedAt
    revoked?.replacedByHash shouldBe "replacement-hash"

    val other = repository.findByHash(untouched.tokenHash)
    other?.revokedAt.shouldBeNull()
    other?.replacedByHash.shouldBeNull()
  }

  test("revoke accepts a null replacedByHash, e.g. revoking without a rotation") {
    val repository = RefreshTokenRepository(database)
    val playerRepository = PlayerRepository(database)
    val player = playerRepository.insertGuest()
    val row = freshTokenRow(player.id)
    repository.insert(row)

    val revokedAt = Instant.parse("2026-01-05T12:00:00Z")
    repository.revoke(row.tokenHash, null, revokedAt)

    val revoked = repository.findByHash(row.tokenHash)
    revoked?.revokedAt shouldBe revokedAt
    revoked?.replacedByHash.shouldBeNull()
  }

  test("revokeAllForPlayer revokes every token for that player, and leaves other players' tokens alone") {
    val repository = RefreshTokenRepository(database)
    val playerRepository = PlayerRepository(database)
    val stolenVictim = playerRepository.insertGuest()
    val bystander = playerRepository.insertGuest()

    val firstOfVictim = freshTokenRow(stolenVictim.id)
    val secondOfVictim = freshTokenRow(stolenVictim.id)
    val bystanderToken = freshTokenRow(bystander.id)
    repository.insert(firstOfVictim)
    repository.insert(secondOfVictim)
    repository.insert(bystanderToken)

    val revokedAt = Instant.parse("2026-01-10T00:00:00Z")
    repository.revokeAllForPlayer(stolenVictim.id, revokedAt)

    repository.findByHash(firstOfVictim.tokenHash)?.revokedAt shouldBe revokedAt
    repository.findByHash(secondOfVictim.tokenHash)?.revokedAt shouldBe revokedAt
    repository.findByHash(bystanderToken.tokenHash)?.revokedAt.shouldBeNull()
  }

  test("deleteAllForPlayer removes every row for that player, and leaves other players' tokens alone") {
    val repository = RefreshTokenRepository(database)
    val playerRepository = PlayerRepository(database)
    val toClear = playerRepository.insertGuest()
    val bystander = playerRepository.insertGuest()

    val firstOfCleared = freshTokenRow(toClear.id)
    val secondOfCleared = freshTokenRow(toClear.id)
    val bystanderToken = freshTokenRow(bystander.id)
    repository.insert(firstOfCleared)
    repository.insert(secondOfCleared)
    repository.insert(bystanderToken)

    repository.deleteAllForPlayer(toClear.id)

    repository.findByHash(firstOfCleared.tokenHash).shouldBeNull()
    repository.findByHash(secondOfCleared.tokenHash).shouldBeNull()
    repository.findByHash(bystanderToken.tokenHash).shouldNotBeNull()
  }
})
