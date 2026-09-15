// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

package br.com.colman.palavramento.server.testsupport

import io.kotest.extensions.testcontainers.JdbcDatabaseContainerExtension
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

private class KotestPostgresContainer(image: String) :
  PostgreSQLContainer<KotestPostgresContainer>(DockerImageName.parse(image))

/**
 * One Postgres container for the whole `:server:test` run (dossier phase 3 task: "share one Postgres
 * container per spec or per project"). [extension] defaults its lifecycle mode to
 * `ContainerLifecycleMode.Project`, so the container starts on the first spec that installs it and
 * stops once every spec has run; every other spec installing the same [extension] instance reuses
 * the container and its pooled [com.zaxxer.hikari.HikariDataSource] instead of starting a new one.
 */
object TestPostgres {
  val extension = JdbcDatabaseContainerExtension(KotestPostgresContainer("postgres:16-alpine"))
}
