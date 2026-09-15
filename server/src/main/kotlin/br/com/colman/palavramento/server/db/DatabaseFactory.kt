// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.db

import br.com.colman.palavramento.server.config.ServerConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import javax.sql.DataSource

/**
 * Builds the HikariCP pool, runs Flyway migrations against it and connects Exposed to the same pool
 * (ADR 0003: SQL-versioned schema, so production and Testcontainers-backed tests apply the exact
 * same steps). Kept as one small factory instead of separate Koin definitions for the DataSource and
 * the migration step, because migrating must always happen before Exposed's [Database] is used, and
 * a single ordered function makes that dependency impossible to get backwards by accident.
 */
object DatabaseFactory {

  /** Creates the pool, migrates it and returns the connected Exposed [Database]. */
  fun connect(config: ServerConfig): Database {
    val dataSource = hikariDataSource(config)
    migrate(dataSource)
    return Database.connect(dataSource)
  }

  /** Migrates an already-created [DataSource] without touching Exposed, e.g. a Testcontainers pool. */
  fun migrate(dataSource: DataSource) {
    Flyway.configure()
      .dataSource(dataSource)
      .locations("classpath:db/migration")
      .load()
      .migrate()
  }

  private fun hikariDataSource(config: ServerConfig): HikariDataSource {
    val hikariConfig = HikariConfig().apply {
      jdbcUrl = config.databaseUrl
      username = config.databaseUser
      password = config.databasePassword
      driverClassName = "org.postgresql.Driver"
      maximumPoolSize = MaxPoolSize
    }
    return HikariDataSource(hikariConfig)
  }

  private const val MaxPoolSize = 10
}
