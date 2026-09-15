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

  testImplementation(libs.bundles.kotest)

  detektPlugins(libs.detekt.formatting)
}

detekt {
  buildUponDefaultConfig = true
  config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

tasks.named("check") {
  dependsOn("detekt")
}
