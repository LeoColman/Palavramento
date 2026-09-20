// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Leonardo Colman Lopes

import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.detekt)
  alias(libs.plugins.sqldelight)
}

/**
 * Server the app talks to: the production server by default (ADR 0013). To play against a local
 * server, set `palavramento.serverUrl` as a Gradle property (-P) or in the unversioned
 * `local.properties`: `http://10.0.2.2:8080` from the emulator, the machine's LAN address from a phone.
 */
val serverUrl: String = providers.gradleProperty("palavramento.serverUrl").orNull
  ?: rootProject.file("local.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }.getProperty("palavramento.serverUrl")
  }
  ?: "https://palavramento.colman.com.br"

/**
 * Release signing (ADR 0017). The keystore and its passwords live in the repository only as
 * git-secret ciphertext (`*.secret`); `git secret reveal` writes the plaintext back for whoever holds
 * an authorized GPG key. When the plaintext is absent, which is the normal state of a fresh clone and
 * of any CI job without the key, the release variant stays unsigned instead of failing the build, so
 * `check`, `assembleDebug` and the whole test suite keep working for everyone.
 */
val keystoreProperties: Properties? = rootProject.file("keystore.properties")
  .takeIf { it.exists() }
  ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

android {
  namespace = "br.com.colman.palavramento"
  // Compose 1.12 and Navigation 2.10 require compiling against API 37; targetSdk stays at 36.
  compileSdk = 37

  defaultConfig {
    applicationId = "br.com.colman.palavramento"
    minSdk = 26
    targetSdk = 36
    versionCode = 3
    versionName = "1.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // See serverUrl above. A production build will need its own HTTPS address.
    buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
  }

  signingConfigs {
    keystoreProperties?.let { properties ->
      create("release") {
        storeFile = rootProject.file(properties.getProperty("storeFile"))
        storePassword = properties.getProperty("storePassword")
        keyAlias = properties.getProperty("keyAlias")
        keyPassword = properties.getProperty("keyPassword")
      }
    }
  }

  buildTypes {
    named("release") {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Null without the revealed keystore: the APK is then built unsigned, exactly as before.
      signingConfig = signingConfigs.findByName("release")
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

/**
 * Classpath used to launch PIT, kept apart so the mutation engine never leaks into the APK.
 */
val pitest: Configuration by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
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

  // Kotest ships its own PIT test plugin, so PIT drives the Kotest engine directly.
  pitest(libs.pitest.command.line)
  pitest(libs.kotest.pitest)

  detektPlugins(libs.detekt.formatting)
}

detekt {
  buildUponDefaultConfig = true
  config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

// Mutation score is a build gate (dossier §10), not a weekly report.
tasks.named("check") {
  dependsOn("detekt", "pitest")
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

/**
 * Classes PIT does not mutate in `:app`, and why (ADR 0016):
 * - Compose screens (`*ScreenKt`, `*ViewKt`, `*SheetKt`, `ComposableSingletons*`, the theme and the
 *   nav host): the Compose compiler rewrites every composable into group/skip bookkeeping whose
 *   branches a JVM unit test cannot reach at all. What is testable about them (the gesture, the
 *   countdown, tile rendering) is covered by the instrumented specs in `src/androidTest`, which run
 *   on a device and are invisible to PIT.
 * - `MainActivity`, `PalavramentoApplication`, `AndroidGameAudio`, the SQLDelight Android driver
 *   wiring: Android framework entry points with no JVM-side behavior to assert.
 * - `br.com.colman.palavramento.data` SQLDelight output (`Database*`, `*Queries*`, the three table
 *   row types): generated code, not ours to test. The hand written repositories in the same package
 *   stay in scope.
 * - `*$$serializer` and `*$inlined$*`: bytecode the Kotlin compiler writes.
 */
val excludedFromMutation = listOf(
  "*\$\$serializer",
  "*\$inlined\$*",
  "*ComposableSingletons*",
  "br.com.colman.palavramento.MainActivity*",
  "br.com.colman.palavramento.PalavramentoApplication*",
  "br.com.colman.palavramento.audio.AndroidGameAudio*",
  "br.com.colman.palavramento.data.Database*",
  "br.com.colman.palavramento.data.Profile",
  "br.com.colman.palavramento.data.RoundHistory",
  "br.com.colman.palavramento.data.LifetimeStatsCache",
  "br.com.colman.palavramento.data.*Queries*",
  "br.com.colman.palavramento.data.shadow.*",
  "br.com.colman.palavramento.ui.*ScreenKt*",
  "br.com.colman.palavramento.ui.*ViewKt*",
  "br.com.colman.palavramento.ui.*SheetKt*",
  "br.com.colman.palavramento.ui.navigation.*",
  "br.com.colman.palavramento.ui.theme.*",
  "br.com.colman.palavramento.ui.common.CountdownKt*",
  "br.com.colman.palavramento.ui.common.FlipCountdownKt*",
  "br.com.colman.palavramento.ui.common.MotionPreferenceKt*",
).joinToString(",")

/**
 * Mutation testing on the Android module, gated at 50% killed mutants (dossier §10, ADR 0016).
 *
 * The gate is half the JVM modules' because half of `:app` is Compose and Android framework code
 * that only the instrumented specs can reach, so a JVM-only mutation run can never score like
 * `:domain` or `:server` does. It still guards the part that decides anything: the reducer, the
 * view models, the session, the queues and the repositories.
 *
 * PIT mutates the debug variant's Kotlin output and runs against the unit test task's own runtime
 * classpath, which is where the mockable `android.jar` comes from.
 */
val pitestClasspathFile = layout.buildDirectory.file("tmp/pitest/classpath.txt")

/** AGP's own intermediate holding just this module's compiled classes, the ones PIT should mutate. */
val AppClassesJar = "intermediates/runtime_app_classes_jar/debug"

val pitestTask = tasks.register<JavaExec>("pitest") {
  group = "verification"
  description = "Runs PIT mutation testing on the debug variant and fails below 50% killed mutants"

  val sourceDirs = files("src/main/kotlin")
  val reportDir = layout.buildDirectory.dir("reports/pitest")
  val classpathFile = pitestClasspathFile

  javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(17) }
  mainClass = "org.pitest.mutationtest.commandline.MutationCoverageReport"
  outputs.dir(reportDir).withPropertyName("report")
  // The launcher only coordinates: the work happens in the minions it forks.
  maxHeapSize = "1g"

  args(
    "--classPathFile=${classpathFile.get().asFile.absolutePath}",
    "--sourceDirs=${sourceDirs.joinToString(",") { it.absolutePath }}",
    "--reportDir=${reportDir.get().asFile.absolutePath}",
    "--targetClasses=br.com.colman.palavramento.*",
    // PIT only hands specs matching this glob to the engine: every Kotest spec must be named *Test.
    "--targetTests=br.com.colman.palavramento.*Test",
    "--excludedClasses=$excludedFromMutation",
    "--testPlugin=Kotest",
    "--mutationThreshold=50",
    // Coroutine tests with virtual time are slow enough that PIT's 4s default kills healthy minions.
    "--timeoutConst=15000",
    "--outputFormats=HTML,XML",
    "--timestampedReports=false",
    "--failWhenNoMutations=false",
    // Without an explicit cap each minion takes the JVM default (a quarter of the machine's RAM),
    // multiplied by --threads and stacked on top of the Gradle daemon's own 4 GB. That is an OOM.
    "--jvmArgs=-Xmx768m",
  )
}

// AGP only registers the unit test task in its own afterEvaluate, so its runtime classpath (the one
// carrying the mockable android.jar) can only be read once the variants exist.
afterEvaluate {
  val unitTestClasspath = tasks.named<Test>("testDebugUnitTest").get().classpath

  pitestTask.configure {
    // Test runtime first, so PIT's minions load exactly the jars the unit test task loads.
    classpath(unitTestClasspath)
    classpath(configurations["pitest"])
    inputs.files(unitTestClasspath).withPropertyName("codeUnderTest")

    doFirst {
      pitestClasspathFile.get().asFile.apply {
        parentFile.mkdirs()
        writeText(unitTestClasspath.filter { it.exists() }.joinToString("\n") { it.absolutePath })
      }
      // PIT only mutates classes it also finds on the classpath, and AGP puts this module's own
      // classes there as a jar, not as the `tmp/kotlin-classes/debug` directory that produced it.
      // Pointing at the directory instead is what makes PIT report "0 mutation test units".
      val appClasses = unitTestClasspath.files.first { AppClassesJar in it.invariantSeparatorsPath }
      args("--mutableCodePaths=${appClasses.absolutePath}")
      // Added here so the core count of whichever machine runs the build stays out of the inputs.
      // Half the cores, like `:server`: every PIT thread is a forked JVM sitting next to the Gradle
      // daemon's own heap, and taking all of them is what gets the build killed on a 16 GB laptop.
      args("--threads=${maxOf(1, Runtime.getRuntime().availableProcessors() / 2)}")
    }
  }
}
