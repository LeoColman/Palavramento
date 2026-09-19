// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.testsupport

import br.com.colman.palavramento.server.db.DatabaseFactory
import io.kotest.core.extensions.install
import io.kotest.core.spec.Spec
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.UUID

/**
 * This JVM's database, migrated (idempotent: Flyway no-ops once the schema is already current,
 * dossier ADR 0003) and returned as an Exposed [Database]. It comes from the shared server when the
 * build handed this JVM one, and from the [TestPostgres] container installed into [this] spec
 * otherwise. See [TestPostgres] for which run uses which.
 */
fun Spec.testDatabase(): Database {
  val dataSource = TestPostgres.sharedServerDataSource ?: install(TestPostgres.extension)
  DatabaseFactory.migrate(dataSource)
  return Database.connect(dataSource)
}

/** A fresh room id per test (dossier phase 3 task: share one container, so each test needs its own room). */
fun testRoomId(): String = "room-${UUID.randomUUID()}"
