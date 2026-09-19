// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.auth

import br.com.colman.palavramento.server.config.ServerConfig
import br.com.colman.palavramento.server.db.tables.PlayersTable
import br.com.colman.palavramento.server.repository.GuestAuthProvider
import br.com.colman.palavramento.server.repository.PasswordAuthProvider
import br.com.colman.palavramento.server.repository.PlayerRepository
import br.com.colman.palavramento.server.repository.PlayerRow
import br.com.colman.palavramento.server.repository.PlayerStatsRepository
import br.com.colman.palavramento.server.repository.RefreshTokenRepository
import br.com.colman.palavramento.server.repository.RefreshTokenRow
import br.com.colman.palavramento.server.repository.RoundRepository
import br.com.colman.palavramento.server.repository.RoundResultRepository
import br.com.colman.palavramento.server.repository.RoundResultRow
import br.com.colman.palavramento.server.repository.SubmissionRepository
import br.com.colman.palavramento.server.round.AcceptedSubmission
import br.com.colman.palavramento.server.round.MutableGameClock
import br.com.colman.palavramento.server.testsupport.insertFakeFinishedRound
import br.com.colman.palavramento.server.testsupport.testDatabase
import br.com.colman.palavramento.server.testsupport.testRoomId
import br.com.colman.palavramento.server.testsupport.testServerConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.time.Instant
import java.util.UUID

private const val Password = "correct horse battery staple"

private fun authService(
  database: Database,
  clock: MutableGameClock,
  config: ServerConfig = testServerConfig(),
): AuthService = AuthService(
  config = config,
  clock = clock,
  jwtService = JwtService(config, clock),
  playerRepository = PlayerRepository(database),
  refreshTokenRepository = RefreshTokenRepository(database),
  submissionRepository = SubmissionRepository(database),
  roundResultRepository = RoundResultRepository(database),
  playerStatsRepository = PlayerStatsRepository(database),
)

/**
 * Deletes [playerId]'s row while leaving its `refresh_tokens` row in place, bypassing the foreign
 * key check for this transaction only (`SET LOCAL`, reverted automatically at commit). No real code
 * path produces this state (`AuthService`'s own migration always deletes a guest's tokens before its
 * player row), but [AuthService.refresh] still guards against it, so a test needs a direct way to
 * construct it.
 */
private suspend fun forciblyDeletePlayerLeavingItsTokens(database: Database, playerId: String) {
  suspendTransaction(database) {
    exec("SET LOCAL session_replication_role = replica")
    PlayersTable.deleteWhere { PlayersTable.id eq playerId }
  }
}

/**
 * [AuthService] exercised directly (dossier §8, ADR 0007), rather than only through
 * `rest/AuthRoutesTest`'s HTTP layer: every [RegisterOutcome]/[LoginOutcome]/[RefreshOutcome]
 * branch, the guest promotion and login migration paths, and refresh token rotation/reuse/expiry.
 */
class AuthServiceTest : FunSpec({
  val database = testDatabase()

  test("guest creates a fresh guest player row with a default name derived from its id") {
    val service = authService(database, MutableGameClock())
    val playerRepository = PlayerRepository(database)

    val tokens = service.guest(null)

    tokens.isGuest shouldBe true
    tokens.displayName shouldBe "Convidado ${tokens.playerId.take(4).uppercase()}"

    val stored = playerRepository.findById(tokens.playerId)
    stored.shouldNotBeNull()
    stored.isGuest shouldBe true
    stored.authProvider shouldBe GuestAuthProvider
    stored.email.shouldBeNull()
    stored.passwordHash.shouldBeNull()
  }

  test("guest trims a provided display name, and a blank one falls back to the default") {
    val service = authService(database, MutableGameClock())

    val named = service.guest("  Legolas  ")
    named.displayName shouldBe "Legolas"

    val blank = service.guest("   ")
    blank.displayName shouldBe "Convidado ${blank.playerId.take(4).uppercase()}"
  }

  test("guest issues tokens whose access and refresh expiry follow the clock and configured TTLs") {
    val issuedAt = Instant.parse("2026-01-01T00:00:00Z")
    val clock = MutableGameClock(issuedAt)
    val config = testServerConfig()
    val service = authService(database, clock, config)
    val refreshTokenRepository = RefreshTokenRepository(database)

    val tokens = service.guest("Timely")

    tokens.accessTokenExpiresAt shouldBe issuedAt.plusMillis(config.accessTokenTtl.inWholeMilliseconds).toEpochMilli()

    val storedRefresh = refreshTokenRepository.findByHash(TokenHasher.hash(tokens.refreshToken))
    storedRefresh.shouldNotBeNull()
    storedRefresh.playerId shouldBe tokens.playerId
    storedRefresh.createdAt shouldBe issuedAt
    storedRefresh.expiresAt shouldBe issuedAt.plusMillis(config.refreshTokenTtl.inWholeMilliseconds)
    storedRefresh.revokedAt.shouldBeNull()
    storedRefresh.replacedByHash.shouldBeNull()
  }

  test("register without a guest id creates a brand new registered account") {
    val service = authService(database, MutableGameClock())
    val playerRepository = PlayerRepository(database)
    val email = "new-${UUID.randomUUID()}@example.com"

    val outcome = service.register(null, email, Password, "Newcomer")

    outcome.shouldBeInstanceOf<RegisterOutcome.Success>()
    outcome.tokens.isGuest shouldBe false
    outcome.tokens.displayName shouldBe "Newcomer"

    val stored = playerRepository.findByEmail(email)
    stored.shouldNotBeNull()
    stored.isGuest shouldBe false
    stored.authProvider shouldBe PasswordAuthProvider
    stored.displayName shouldBe "Newcomer"
    PasswordHasher.verify(Password, stored.passwordHash!!) shouldBe true
  }

  test("register rejects an email already in use, even for a guest promotion attempt, without touching the guest") {
    val service = authService(database, MutableGameClock())
    val playerRepository = PlayerRepository(database)
    val takenEmail = "taken-${UUID.randomUUID()}@example.com"
    service.register(null, takenEmail, Password, "First")

    service.register(null, takenEmail, Password, "Second") shouldBe RegisterOutcome.EmailTaken

    val guest = service.guest("StillAGuest")
    service.register(guest.playerId, takenEmail, Password, "Second") shouldBe RegisterOutcome.EmailTaken
    playerRepository.findById(guest.playerId)?.isGuest shouldBe true
  }

  test("register with an unknown guestPlayerId returns GuestNotFound") {
    val service = authService(database, MutableGameClock())
    val email = "ghost-${UUID.randomUUID()}@example.com"

    service.register(UUID.randomUUID().toString(), email, Password, "Ghost") shouldBe RegisterOutcome.GuestNotFound
  }

  test("register with a guestPlayerId of an already registered account returns NotAGuest and leaves it untouched") {
    val service = authService(database, MutableGameClock())
    val playerRepository = PlayerRepository(database)
    val originalEmail = "original-${UUID.randomUUID()}@example.com"

    val registered = service.register(null, originalEmail, Password, "Original")
    registered.shouldBeInstanceOf<RegisterOutcome.Success>()
    val registeredId = registered.tokens.playerId

    val newEmail = "collide-${UUID.randomUUID()}@example.com"
    service.register(registeredId, newEmail, Password, "Collide") shouldBe RegisterOutcome.NotAGuest

    val stillOriginal = playerRepository.findById(registeredId)
    stillOriginal?.email shouldBe originalEmail
    stillOriginal?.isGuest shouldBe false
  }

  test("register promotes a guest in place: same id, and player_stats rebuilt from its full round history") {
    val roomId = testRoomId()
    val service = authService(database, MutableGameClock())
    val playerRepository = PlayerRepository(database)
    val roundRepository = RoundRepository(database)
    val roundResultRepository = RoundResultRepository(database)
    val submissionRepository = SubmissionRepository(database)
    val playerStatsRepository = PlayerStatsRepository(database)

    val guest = service.guest("Promotable")
    val roundA = roundRepository.insertFakeFinishedRound(roomId)
    val roundB = roundRepository.insertFakeFinishedRound(roomId)
    val roundC = roundRepository.insertFakeFinishedRound(roomId)
    val now = Instant.now()

    playerRepository.transaction {
      roundResultRepository.insert(
        this,
        RoundResultRow(roundA, guest.playerId, score = 42, words = 3, rank = 1, xp = 8, enteredAt = now)
      )
      // A round the guest entered but found nothing in: contributes to gamesPlayed, not gamesCompleted.
      roundResultRepository.insert(
        this,
        RoundResultRow(roundB, guest.playerId, score = 0, words = 0, rank = 5, xp = 0, enteredAt = now)
      )
      roundResultRepository.insert(
        this,
        RoundResultRow(roundC, guest.playerId, score = 70, words = 4, rank = 1, xp = 14, enteredAt = now)
      )
    }
    submissionRepository.insert(AcceptedSubmission(roundA, guest.playerId, "CASA", "casa", 15, listOf(0, 1, 2, 3), now))
    submissionRepository.insert(AcceptedSubmission(roundC, guest.playerId, "SOL", "sol", 20, listOf(4, 5, 6), now))

    val email = "promoted-${UUID.randomUUID()}@example.com"
    val outcome = service.register(guest.playerId, email, Password, "Promoted")

    outcome.shouldBeInstanceOf<RegisterOutcome.Success>()
    outcome.tokens.playerId shouldBe guest.playerId
    outcome.tokens.isGuest shouldBe false

    val promoted = playerRepository.findById(guest.playerId)
    promoted?.isGuest shouldBe false
    promoted?.email shouldBe email

    val stats = playerStatsRepository.get(guest.playerId)
    stats.shouldNotBeNull()
    stats.totalScore shouldBe 112L
    stats.totalWords shouldBe 7L
    stats.bestGameScore shouldBe 70
    stats.gamesPlayed shouldBe 3
    // Only roundA and roundC had words found; roundB (0 words) does not count as completed.
    stats.gamesCompleted shouldBe 2
    stats.bestRank shouldBe 1
    stats.totalXp shouldBe 22L
    // "sol" (score 20) beats "casa" (score 15) regardless of which round is folded in first.
    stats.bestWord shouldBe "sol"
    stats.bestWordScore shouldBe 20
  }

  test("register promotes a guest with no rounds played: no player_stats row is created") {
    val service = authService(database, MutableGameClock())
    val playerStatsRepository = PlayerStatsRepository(database)
    val guest = service.guest("NeverPlayed")

    val email = "neverplayed-${UUID.randomUUID()}@example.com"
    val outcome = service.register(guest.playerId, email, Password, "NeverPlayed")

    outcome.shouldBeInstanceOf<RegisterOutcome.Success>()
    playerStatsRepository.get(guest.playerId).shouldBeNull()
  }

  test("login is rejected for an unknown email, a wrong password, or an account without a password hash") {
    val service = authService(database, MutableGameClock())
    val playerRepository = PlayerRepository(database)

    service.login(null, "nobody-${UUID.randomUUID()}@example.com", Password) shouldBe LoginOutcome.InvalidCredentials

    val email = "wrongpass-${UUID.randomUUID()}@example.com"
    service.register(null, email, Password, "Someone")
    service.login(null, email, "not the right password") shouldBe LoginOutcome.InvalidCredentials

    val noHashEmail = "nohash-${UUID.randomUUID()}@example.com"
    playerRepository.insert(
      PlayerRow(
        id = UUID.randomUUID().toString(),
        displayName = "NoHash",
        isGuest = false,
        authProvider = PasswordAuthProvider,
        email = noHashEmail,
        passwordHash = null,
        createdAt = Instant.now(),
      ),
    )
    service.login(null, noHashEmail, Password) shouldBe LoginOutcome.InvalidCredentials
  }

  test(
    "login as a guest into a different account migrates history: keeps the target's own result on " +
      "conflict, moves the rest, and removes the guest"
  ) {
    val roomId = testRoomId()
    val service = authService(database, MutableGameClock())
    val playerRepository = PlayerRepository(database)
    val roundRepository = RoundRepository(database)
    val roundResultRepository = RoundResultRepository(database)
    val playerStatsRepository = PlayerStatsRepository(database)

    val email = "target-${UUID.randomUUID()}@example.com"
    val targetGuest = service.guest("TargetOwner")
    service.register(targetGuest.playerId, email, Password, "Target")
    val targetId = targetGuest.playerId

    val sharedRound = roundRepository.insertFakeFinishedRound(roomId)
    val guestOnlyRound = roundRepository.insertFakeFinishedRound(roomId)
    val now = Instant.now()

    playerRepository.transaction {
      roundResultRepository.insert(
        this,
        RoundResultRow(sharedRound, targetId, score = 100, words = 5, rank = 1, xp = 20, enteredAt = now)
      )
    }

    val guest = service.guest("MigratingGuest")
    playerRepository.transaction {
      roundResultRepository.insert(
        this,
        RoundResultRow(sharedRound, guest.playerId, score = 50, words = 2, rank = 2, xp = 10, enteredAt = now)
      )
      roundResultRepository.insert(
        this,
        RoundResultRow(guestOnlyRound, guest.playerId, score = 70, words = 3, rank = 1, xp = 14, enteredAt = now)
      )
    }

    val outcome = service.login(guest.playerId, email, Password)

    outcome.shouldBeInstanceOf<LoginOutcome.Success>()
    outcome.tokens.playerId shouldBe targetId

    // Conflict round: the target's own result wins, the guest's duplicate is dropped.
    val sharedResults = roundResultRepository.findByRound(sharedRound)
    sharedResults shouldHaveSize 1
    sharedResults.single().playerId shouldBe targetId
    sharedResults.single().score shouldBe 100

    // Non-conflicting round: the guest's result moves over intact.
    val guestOnlyResults = roundResultRepository.findByRound(guestOnlyRound)
    guestOnlyResults shouldHaveSize 1
    guestOnlyResults.single().playerId shouldBe targetId
    guestOnlyResults.single().score shouldBe 70

    playerRepository.findById(guest.playerId).shouldBeNull()

    val stats = playerStatsRepository.get(targetId)
    stats?.totalScore shouldBe 170L
    stats?.gamesPlayed shouldBe 2
  }

  test("login silently skips migration when the presented guestPlayerId no longer exists") {
    val service = authService(database, MutableGameClock())
    val email = "solo-${UUID.randomUUID()}@example.com"
    service.register(null, email, Password, "Solo")

    val outcome = service.login(UUID.randomUUID().toString(), email, Password)

    outcome.shouldBeInstanceOf<LoginOutcome.Success>()
  }

  test("login silently skips migration when guestPlayerId belongs to a non guest account") {
    val service = authService(database, MutableGameClock())
    val playerRepository = PlayerRepository(database)
    val emailA = "a-${UUID.randomUUID()}@example.com"
    val emailB = "b-${UUID.randomUUID()}@example.com"

    val registeredA = service.register(null, emailA, Password, "AccountA")
    registeredA.shouldBeInstanceOf<RegisterOutcome.Success>()
    val accountAId = registeredA.tokens.playerId
    service.register(null, emailB, Password, "AccountB")

    val outcome = service.login(accountAId, emailB, Password)

    outcome.shouldBeInstanceOf<LoginOutcome.Success>()
    // Account A survives untouched: the guard against migrating a non guest kept it in place.
    val stillA = playerRepository.findById(accountAId)
    stillA.shouldNotBeNull()
    stillA.email shouldBe emailA
    stillA.isGuest shouldBe false
  }

  test("refresh rotates the token: the old one is revoked and linked to the new one") {
    val issuedAt = Instant.parse("2026-01-01T00:00:00Z")
    val clock = MutableGameClock(issuedAt)
    val service = authService(database, clock)
    val refreshTokenRepository = RefreshTokenRepository(database)

    val first = service.guest("Rotator")
    clock.advance(1000)
    val outcome = service.refresh(first.refreshToken)

    outcome.shouldBeInstanceOf<RefreshOutcome.Success>()
    outcome.tokens.playerId shouldBe first.playerId
    outcome.tokens.refreshToken shouldNotBe first.refreshToken
    outcome.tokens.isGuest shouldBe true
    outcome.tokens.displayName shouldBe "Rotator"

    val oldRow = refreshTokenRepository.findByHash(TokenHasher.hash(first.refreshToken))
    oldRow?.revokedAt shouldBe clock.now()
    oldRow?.replacedByHash shouldBe TokenHasher.hash(outcome.tokens.refreshToken)

    val newRow = refreshTokenRepository.findByHash(TokenHasher.hash(outcome.tokens.refreshToken))
    newRow?.revokedAt.shouldBeNull()
    newRow?.playerId shouldBe first.playerId
  }

  test("presenting an already rotated refresh token again is treated as reuse: the whole family is revoked") {
    val service = authService(database, MutableGameClock())
    val refreshTokenRepository = RefreshTokenRepository(database)

    val first = service.guest("Rotator")
    val rotated = service.refresh(first.refreshToken)
    rotated.shouldBeInstanceOf<RefreshOutcome.Success>()

    val reuse = service.refresh(first.refreshToken)
    reuse shouldBe RefreshOutcome.Invalid

    // Reuse revokes the whole chain: even the latest, legitimately-issued token stops working.
    val afterReuse = service.refresh(rotated.tokens.refreshToken)
    afterReuse shouldBe RefreshOutcome.Invalid

    refreshTokenRepository.findByHash(TokenHasher.hash(first.refreshToken))?.revokedAt.shouldNotBeNull()
    refreshTokenRepository.findByHash(TokenHasher.hash(rotated.tokens.refreshToken))?.revokedAt.shouldNotBeNull()
  }

  test("refresh rejects an expired token without revoking the player's other, still valid tokens") {
    val clock = MutableGameClock(Instant.parse("2026-01-01T00:00:00Z"))
    val service = authService(database, clock)
    val refreshTokenRepository = RefreshTokenRepository(database)
    val playerRepository = PlayerRepository(database)
    val guest = service.guest("HasTwoTokens")

    val expiredHash = TokenHasher.hash("expired-raw-token")
    refreshTokenRepository.insert(
      RefreshTokenRow(
        id = UUID.randomUUID().toString(),
        playerId = guest.playerId,
        tokenHash = expiredHash,
        createdAt = clock.now().minusSeconds(120),
        expiresAt = clock.now().minusSeconds(1),
        revokedAt = null,
        replacedByHash = null,
      ),
    )

    val outcome = service.refresh("expired-raw-token")

    outcome shouldBe RefreshOutcome.Invalid
    // The guest's own account creation token, still valid, must not have been swept up.
    refreshTokenRepository.findByHash(TokenHasher.hash(guest.refreshToken))?.revokedAt.shouldBeNull()
    playerRepository.findById(guest.playerId).shouldNotBeNull()
  }

  test("refresh of a token that was never issued is rejected") {
    val service = authService(database, MutableGameClock())

    service.refresh("this-token-does-not-exist") shouldBe RefreshOutcome.Invalid
  }

  test("refresh fails once the player behind a still valid, unexpired token has disappeared") {
    val service = authService(database, MutableGameClock())
    val guest = service.guest("Ghost")

    forciblyDeletePlayerLeavingItsTokens(database, guest.playerId)

    service.refresh(guest.refreshToken) shouldBe RefreshOutcome.Invalid
  }

  test("refresh reflects the player's current identity, not the one from token issuance") {
    val service = authService(database, MutableGameClock())
    val guest = service.guest("BeforeName")

    val email = "current-identity-${UUID.randomUUID()}@example.com"
    service.register(guest.playerId, email, Password, "AfterName")

    // The guest's original refresh token is untouched by promotion, and still works.
    val outcome = service.refresh(guest.refreshToken)

    outcome.shouldBeInstanceOf<RefreshOutcome.Success>()
    outcome.tokens.playerId shouldBe guest.playerId
    outcome.tokens.isGuest shouldBe false
    outcome.tokens.displayName shouldBe "AfterName"
  }
})
