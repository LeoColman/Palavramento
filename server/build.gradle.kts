// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

plugins {
  application
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ktor)
  alias(libs.plugins.detekt)
}

application {
  mainClass = "br.com.colman.palavramento.server.ApplicationKt"
}

kotlin {
  jvmToolchain(21)
}

dependencies {
  implementation(project(":domain"))

  implementation(libs.bundles.ktor.server)
  implementation(libs.logback)

  testImplementation(libs.bundles.kotest)
  testImplementation(libs.ktor.server.test.host)

  detektPlugins(libs.detekt.formatting)
}

tasks.test {
  useJUnitPlatform()
}

detekt {
  buildUponDefaultConfig = true
  config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}
