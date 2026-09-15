// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.detekt)
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
