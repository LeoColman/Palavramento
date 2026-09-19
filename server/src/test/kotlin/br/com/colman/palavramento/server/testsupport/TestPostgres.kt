// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.testsupport

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.extensions.testcontainers.JdbcDatabaseContainerExtension
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.util.UUID

private class KotestPostgresContainer(image: String) :
  PostgreSQLContainer<KotestPostgresContainer>(DockerImageName.parse(image))

/** JDBC url of a Postgres the build already started, handed to this JVM by `:server:pitest`. */
const val SharedPostgresUrlProperty = "palavramento.test.postgres.url"

private const val SharedPostgresUser = "postgres"
private const val SharedPostgresPassword = "postgres"

// Matches what JdbcDatabaseContainerExtension pools for the container path: the scheduler and the
// WebSocket route hold several connections at once, and a smaller pool starves them.
private const val SharedPostgresPoolSize = 10

/**
 * Where a spec's database comes from, in the two ways this project runs specs.
 *
 * Under `:server:test` it is [extension]: one container for the whole run (dossier phase 3 task:
 * "share one Postgres container per spec or per project"). [JdbcDatabaseContainerExtension] defaults
 * its lifecycle to `ContainerLifecycleMode.Project`, so the container starts on the first spec that
 * installs it and stops once every spec has run, and every other spec installing the same extension
 * instance reuses the container and its pooled `HikariDataSource`.
 *
 * Under `:server:pitest` it is [sharedServerDataSource]. PIT forks a JVM per mutation unit, over a
 * hundred of them, and a container each was what made that task a five hour run in CI (ADR 0016).
 * The build starts one Postgres up front and names it in [SharedPostgresUrlProperty]; each fork then
 * pays a `CREATE DATABASE` instead of a container boot and still gets a database of its own, so
 * forks running side by side never see each other's rows.
 */
object TestPostgres {

  val extension = JdbcDatabaseContainerExtension(KotestPostgresContainer("postgres:16-alpine"))

  /** A pool on this JVM's own database, or null when no shared server was handed to it. */
  val sharedServerDataSource: HikariDataSource? by lazy {
    System.getProperty(SharedPostgresUrlProperty)?.let(::createOwnDatabase)
  }
}

/**
 * Creates a database named after a fresh UUID on the server [adminUrl] points at, and returns a pool
 * connected to it. The database is never dropped: the container the build started for the run takes
 * every one of them with it when it stops.
 */
private fun createOwnDatabase(adminUrl: String): HikariDataSource {
  val name = "palavramento_test_${UUID.randomUUID().toString().replace("-", "")}"
  DriverManager.getConnection(adminUrl, SharedPostgresUser, SharedPostgresPassword).use { connection ->
    connection.createStatement().use { statement -> statement.executeUpdate("""CREATE DATABASE "$name"""") }
  }
  return HikariDataSource(
    HikariConfig().apply {
      jdbcUrl = adminUrl.replaceAfterLast('/', name)
      username = SharedPostgresUser
      password = SharedPostgresPassword
      driverClassName = "org.postgresql.Driver"
      maximumPoolSize = SharedPostgresPoolSize
    },
  )
}
