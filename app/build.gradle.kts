// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.detekt)
  alias(libs.plugins.sqldelight)
}

android {
  namespace = "br.com.colman.palavramento"
  // Compose 1.12 and Navigation 2.10 require compiling against API 37; targetSdk stays at 36.
  compileSdk = 37

  defaultConfig {
    applicationId = "br.com.colman.palavramento"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // The emulator's own view of the host machine (dossier 5, task brief). Overridable per build
    // type/flavor once phase 5 or later needs a real staging/production server.
    buildConfigField("String", "SERVER_URL", "\"http://10.0.2.2:8080\"")
  }

  buildTypes {
    named("release") {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all { it.useJUnitPlatform() }
    }

    // The `Pixel_9a` AVD in this environment runs a very new preview system image (API 37.1)
    // whose platform removed a private method (`InputManager.getInstance()`) that Espresso's event
    // injector still looks up reflectively, crashing every Compose UI test before it can run. The
    // aosp-atd API 34 image is what the reference project (Petals) also settled on for exactly this
    // reason: no Play services/UI shell (fast to boot) and old enough to match the test libraries.
    managedDevices {
      localDevices {
        create("pixel6Api34") {
          device = "Pixel 6"
          apiLevel = 34
          systemImageSource = "aosp-atd"
          // AGP 10 flips the default to arm64-v8a, which aosp-atd images cannot translate.
          testedAbi = "x86_64"
        }
      }
    }
  }

  packaging {
    resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation(project(":domain"))

  implementation(libs.bundles.compose)
  debugImplementation(libs.compose.ui.tooling)

  implementation(libs.bundles.koin.android)

  implementation(libs.bundles.ktor.client)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.android)

  implementation(libs.datastore.preferences)

  implementation(libs.sqldelight.android.driver)
  implementation(libs.sqldelight.coroutines)
  testImplementation(libs.sqldelight.sqlite.driver)

  testImplementation(libs.bundles.kotest)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.koin.test)
  testImplementation(libs.ktor.client.mock)
  testImplementation(libs.mockk)

  androidTestImplementation(libs.bundles.android.test)
  androidTestImplementation(libs.compose.ui.test.junit4)
  debugImplementation(libs.compose.ui.test.manifest)

  detektPlugins(libs.detekt.formatting)
}

detekt {
  buildUponDefaultConfig = true
  config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

tasks.named("check") {
  dependsOn("detekt")
}

// Local cache (dossier 7, ADR 0009): profile, lifetime stats and the last 50 rounds.
sqldelight {
  databases {
    create("Database") {
      packageName.set("br.com.colman.palavramento.data")
      dialect(libs.sqldelight.sqlite.dialect)
      schemaOutputDirectory = file("src/main/sqldelight/databases")
      verifyMigrations = true
    }
  }
}

// Workaround (task brief, docs/adr/0009-persistencia-local-e-auth.md): SQLDelight 2.3.x uses
// variant.sources.java?.addGeneratedSourceDirectory for AGP < 9.0, but KGP 2.x does not include
// java-registered generated sources in Kotlin compilation under AGP 9's legacy variant API
// (gradle.properties opts out of AGP 9's built-in Kotlin/new DSL for exactly this). Manually wire
// the generate task output dir into KotlinCompile sources, same fix as the reference project (Petals).
afterEvaluate {
  android.applicationVariants.all {
    val variantName = name
    val capitalizedName = variantName.replaceFirstChar { it.uppercase() }
    val sqldelightDir = layout.buildDirectory.dir("generated/sqldelight/code/Database/$variantName")
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>()
      .matching { it.name == "compile${capitalizedName}Kotlin" }
      .configureEach {
        dependsOn("generate${capitalizedName}DatabaseInterface")
        source(sqldelightDir)
      }
  }
}
