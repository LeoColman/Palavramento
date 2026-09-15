// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "palavramento"

include(":domain", ":server")

// The server's Docker build has no Android SDK (deploy/Dockerfile), so it passes
// -Ppalavramento.skipApp=true and leaves the app module out of the build.
if (providers.gradleProperty("palavramento.skipApp").orNull != "true") {
  include(":app")
}
