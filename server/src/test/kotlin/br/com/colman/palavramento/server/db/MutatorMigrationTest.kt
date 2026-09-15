// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.db

import br.com.colman.palavramento.domain.board.Board
import br.com.colman.palavramento.domain.board.Tile
import br.com.colman.palavramento.domain.mutator.Mutator
import br.com.colman.palavramento.domain.protocol.AuthTokens
import br.com.colman.palavramento.domain.protocol.GuestAuthRequest
import br.com.colman.palavramento.domain.protocol.RoundHistoryEntry
import br.com.colman.palavramento.server.db.tables.RoundsTable
import br.com.colman.palavramento.server.module
import br.com.colman.palavramento.server.repository.PlayerRepository
import br.com.colman.palavramento.server.repository.RoundRepository
import br.com.colman.palavramento.server.repository.RoundResultRepository
import br.com.colman.palavramento.server.repository.RoundResultRow
import br.com.colman.palavramento.server.round.RoundRecord
import br.com.colman.palavramento.server.round.RoundStatus
import br.com.colman.palavramento.server.testsupport.TestLexicon
import br.com.colman.palavramento.server.testsupport.testHttpClient
import br.com.colman.palavramento.server.testsupport.testRoomId
import br.com.colman.palavramento.server.testsupport.testServerConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.util.UUID

private class MigrationPostgresContainer(image: String) :
  PostgreSQLContainer<MigrationPostgresContainer>(DockerImageName.parse(image))

private fun hikariDataSourceFor(container: PostgreSQLContainer<*>): HikariDataSource {
  val config = HikariConfig().apply {
    jdbcUrl = container.jdbcUrl
    username = container.username
    password = container.password
    driverClassName = "org.postgresql.Driver"
    maximumPoolSize = 5
  }
  return HikariDataSource(config)
}

/** Overwrites a round's persisted mutator/theme columns directly, simulating a pre-ADR-0012 row. */
private suspend fun overwriteAsLegacyRound(
  database: Database,
  roundId: String,
  mutatorJson: String,
  themeTitle: String,
) {
  suspendTransaction(database) {
    RoundsTable.update({ RoundsTable.id eq roundId }) {
      it[RoundsTable.mutatorJson] = mutatorJson
      it[RoundsTable.themeTitle] = themeTitle
    }
  }
}

private suspend fun mutatorJsonOf(database: Database, roundId: String): String = suspendTransaction(database) {
  RoundsTable.selectAll().where { RoundsTable.id eq roundId }.single()[RoundsTable.mutatorJson]
}

/**
 * ADR 0012: the local database holds finished rounds whose `rounds.mutator_json` is
 * `LETRA_PROIBIDA`/`TAMANHO_MINIMO`, removed from the [Mutator] sealed interface. The `V3` Flyway
 * migration rewrites those to `SEM_MUTADOR` so they decode again. This spec runs its own dedicated,
 * short-lived Postgres container (not the shared one `testDatabase()` uses) because it needs to
 * migrate only up to `V2`, plant a legacy row by hand, then apply `V3` and observe the rewrite - the
 * shared container is already fully migrated by the time most specs run, so `V3` would already be a
 * no-op on it.
 */
class MutatorMigrationTest : FunSpec({
  test("V3 rewrites legacy mutator_json to SEM_MUTADOR; history and restart recovery keep working") {
    val container = MigrationPostgresContainer("postgres:16-alpine")
    container.start()
    val dataSource = hikariDataSourceFor(container)
    try {
      Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("2"))
        .load()
        .migrate()

      val database = Database.connect(dataSource)
      val roundRepository = RoundRepository(database)
      val roomId = testRoomId()
      val now = Instant.now()
      val board = Board(4, List(16) { Tile("A", 1) })

      val finishedRoundId = UUID.randomUUID().toString()
      roundRepository.insert(
        RoundRecord(
          id = finishedRoundId,
          roomId = roomId,
          seed = 0L,
          board = board,
          mutator = Mutator.NoMutator,
          themeTitle = "Grade padrão",
          themeSubtitle = "0 palavras comuns",
          commonMin = 0,
          maxScore = 0,
          maxWords = 0,
          startsAt = now,
          endsAt = now.plusSeconds(1),
          status = RoundStatus.Finished,
        ),
        emptyList(),
      )
      overwriteAsLegacyRound(
        database,
        finishedRoundId,
        """{"type":"TAMANHO_MINIMO","length":5}""",
        "Mínimo de 5 letras",
      )

      val pendingRoundId = UUID.randomUUID().toString()
      roundRepository.insert(
        RoundRecord(
          id = pendingRoundId,
          roomId = roomId,
          seed = 0L,
          board = board,
          mutator = Mutator.NoMutator,
          themeTitle = "Grade padrão",
          themeSubtitle = "0 palavras comuns",
          commonMin = 0,
          maxScore = 0,
          maxWords = 0,
          startsAt = now.plusSeconds(120),
          endsAt = now.plusSeconds(240),
          status = RoundStatus.Scheduled,
        ),
        emptyList(),
      )
      overwriteAsLegacyRound(
        database,
        pendingRoundId,
        """{"type":"LETRA_PROIBIDA","letter":"A"}""",
        "Letra A proibida",
      )

      // Apply V3 (and any later migration): this is the rewrite under test.
      DatabaseFactory.migrate(dataSource)

      mutatorJsonOf(database, finishedRoundId) shouldBe """{"type":"SEM_MUTADOR"}"""
      mutatorJsonOf(database, pendingRoundId) shouldBe """{"type":"SEM_MUTADOR"}"""

      // Decoding no longer throws, and the original theme text (stored separately) is untouched.
      val finishedRecord = roundRepository.findById(finishedRoundId)
      finishedRecord?.mutator shouldBe Mutator.NoMutator
      finishedRecord?.themeTitle shouldBe "Mínimo de 5 letras"

      // Restart recovery (ADR 0007): RoomScheduler resumes from findPending, which must decode too.
      val pending = roundRepository.findPending(roomId, limit = 10)
      pending.map { it.id } shouldContain pendingRoundId
      pending.first { it.id == pendingRoundId }.mutator shouldBe Mutator.NoMutator
      pending.first { it.id == pendingRoundId }.themeTitle shouldBe "Letra A proibida"

      // /players/me/rounds (ADR 0012 task): a live app booted against this now-migrated database.
      testApplication {
        application { module(testServerConfig(), database, TestLexicon.lexicon, roomId = roomId) }
        val client = testHttpClient()
        val guest: AuthTokens = client.post("/auth/guest") {
          contentType(ContentType.Application.Json)
          setBody(GuestAuthRequest("LegacyPlayer"))
        }.body()

        val playerRepository = PlayerRepository(database)
        val roundResultRepository = RoundResultRepository(database)
        playerRepository.transaction {
          roundResultRepository.insert(
            this,
            RoundResultRow(
              finishedRoundId,
              guest.playerId,
              score = 1,
              words = 1,
              rank = 1,
              xp = 1,
              enteredAt = now,
            ),
          )
        }

        val response = client.get(
          "/players/me/rounds"
        ) { header(HttpHeaders.Authorization, "Bearer ${guest.accessToken}") }
        response.status shouldBe HttpStatusCode.OK
        val history: List<RoundHistoryEntry> = response.body()
        history.map { it.roundId } shouldContain finishedRoundId
      }
    } finally {
      dataSource.close()
      container.stop()
    }
  }
})
