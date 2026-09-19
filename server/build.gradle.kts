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

// Hunspell expander and lexicon build tooling (dossier §11 phase 1, ADR 0004): a separate source
// set so `unmunch`-style build-time code never ships in the server jar, while still being reachable
// from tests, which exercise it directly instead of only through the compiled artifact.
sourceSets {
  create("lexiconCompiler")
}

val lexiconCompilerImplementation: Configuration = configurations.getByName("lexiconCompilerImplementation")

/**
 * Classpath used to launch PIT, kept apart so the mutation engine never leaks into the server jar.
 */
val pitest: Configuration by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
}

dependencies {
  implementation(project(":domain"))

  implementation(libs.bundles.ktor.server)
  implementation(libs.logback)

  implementation(libs.koin.ktor)
  implementation(libs.koin.logger.slf4j)

  implementation(libs.bundles.exposed)
  implementation(libs.flyway.core)
  implementation(libs.flyway.postgresql)
  implementation(libs.postgresql)
  implementation(libs.hikari)
  implementation(libs.bcrypt)

  lexiconCompilerImplementation(project(":domain"))

  testImplementation(libs.bundles.kotest)
  testImplementation(libs.ktor.server.test.host)
  testImplementation(libs.ktor.client.content.negotiation)
  testImplementation(libs.ktor.client.websockets)
  testImplementation(libs.kotest.testcontainers)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(sourceSets["lexiconCompiler"].output)

  // Kotest ships its own PIT test plugin, so PIT drives the Kotest engine directly.
  pitest(libs.pitest.command.line)
  pitest(libs.kotest.pitest)

  detektPlugins(libs.detekt.formatting)
}

tasks.test {
  useJUnitPlatform()
}

detekt {
  buildUponDefaultConfig = true
  config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

// Vendored Hunspell dictionary and frequency list (ADR 0002), versioned rather than fetched at
// build time so the build never depends on network access.
val lexiconSourceDir = layout.projectDirectory.dir("src/lexicon")
val dicFile = lexiconSourceDir.file("pt_BR.dic")
val affFile = lexiconSourceDir.file("pt_BR.aff")
val frequencyFile = lexiconSourceDir.file("pt_br_full.txt")

val generatedLexiconDir = layout.buildDirectory.dir("generated/lexicon")
val lexiconBinFile = generatedLexiconDir.map { it.file("lexicon.bin") }
val formsTsvFile = generatedLexiconDir.map { it.file("forms.tsv") }

/**
 * Expands the Hunspell dictionary and writes the binary trie artifact plus `forms.tsv` (dossier
 * §2.2, §2.4, ADR 0004). Cacheable: same three source files in, byte-identical outputs out, because
 * [br.com.colman.palavramento.server.lexicon.CanonicalForms]' tie-break makes the whole pipeline
 * deterministic regardless of traversal order.
 */
val compileLexicon = tasks.register<JavaExec>("compileLexicon") {
  group = "build"
  description = "Expands the Hunspell pt_BR dictionary into the binary lexicon trie and forms.tsv"

  val lexiconCompilerOutput = sourceSets["lexiconCompiler"].output
  val lexiconCompilerClasspath = sourceSets["lexiconCompiler"].runtimeClasspath

  inputs.file(dicFile).withPathSensitivity(PathSensitivity.NONE)
  inputs.file(affFile).withPathSensitivity(PathSensitivity.NONE)
  inputs.file(frequencyFile).withPathSensitivity(PathSensitivity.NONE)
  inputs.files(lexiconCompilerClasspath).withNormalizer(ClasspathNormalizer::class)
  outputs.file(lexiconBinFile)
  outputs.file(formsTsvFile)
  outputs.cacheIf { true }

  dependsOn(lexiconCompilerOutput)
  classpath(lexiconCompilerClasspath)
  mainClass = "br.com.colman.palavramento.server.lexicon.LexiconCompilerMainKt"
  // The real dictionary expands into tens of millions of intermediate candidate forms (most of them
  // discarded clitic combinations, see HunspellExpander's kdoc) before filtering settles down.
  maxHeapSize = "3g"

  doFirst {
    args(
      dicFile.asFile.absolutePath,
      affFile.asFile.absolutePath,
      frequencyFile.asFile.absolutePath,
      lexiconBinFile.get().asFile.absolutePath,
      formsTsvFile.get().asFile.absolutePath,
    )
  }
}

// Ships the artifact in the server jar at /lexicon/pt-BR.bin (dossier §11 phase 1). forms.tsv is
// build output for tests only (dossier: "not committed"), never packaged.
tasks.named<ProcessResources>("processResources") {
  dependsOn(compileLexicon)
  from(lexiconBinFile) {
    into("lexicon")
    rename { "pt-BR.bin" }
  }
}

/**
 * Prints the letter frequency table backing `docs/calibracao-letras.md` (dossier §1.3, §11 phase 1).
 * Not part of `check`, not cacheable: it is a report to read, not a build artifact, so it always
 * recomputes. `./gradlew :server:letterFrequencyReport` and paste the output into the doc.
 */
tasks.register<JavaExec>("letterFrequencyReport") {
  group = "help"
  description = "Prints the pt-BR letter frequency table used to calibrate tile values and weights"
  classpath(sourceSets["lexiconCompiler"].runtimeClasspath)
  dependsOn(sourceSets["lexiconCompiler"].output)
  mainClass = "br.com.colman.palavramento.server.lexicon.LetterFrequencyReportKt"
  maxHeapSize = "3g"
  args(dicFile.asFile.absolutePath, affFile.asFile.absolutePath, frequencyFile.asFile.absolutePath)
}

// Tests read forms.tsv straight off disk (acceptance tests, dossier §11 phase 1) and the artifact
// through the classpath resource processResources produces above.
tasks.test {
  dependsOn(compileLexicon)
  systemProperty("palavramento.lexicon.formsTsv", formsTsvFile.get().asFile.absolutePath)
  // LexiconLoaderTest loads the ~85MB real artifact and independently recomputes the collapsing rule
  // over all 2M+ lines of forms.tsv to check it, which needs more than the default worker heap.
  maxHeapSize = "3g"
}

/**
 * Classes PIT does not mutate, and why (ADR 0016):
 * - `*$$serializer` and `*$inlined$*`: bytecode the Kotlin compiler writes, not ours to test.
 * - `ApplicationKt`: the `embeddedServer` bootstrap. Running it is starting the server, and the
 *   tests drive `Application.module` directly through Ktor's test host instead.
 * - `plugins.*` and `db.DatabaseFactory`: install/DI/Hikari wiring. Every mutant either changes a
 *   framework default no assertion can see or breaks every test at once, which measures nothing.
 * - `db.tables.*`: Exposed column declarations. There is no branch to mutate, only names and types,
 *   and the migration test already checks the schema they produce.
 */
val excludedFromMutation = listOf(
  "*\$\$serializer",
  "*\$inlined\$*",
  "br.com.colman.palavramento.server.ApplicationKt",
  "br.com.colman.palavramento.server.plugins.*",
  "br.com.colman.palavramento.server.db.DatabaseFactory*",
  "br.com.colman.palavramento.server.db.tables.*",
).joinToString(",")

/**
 * Specs PIT never runs, and why (ADR 0016). Both keep running under `test`, where they belong:
 * - [br.com.colman.palavramento.server.lexicon.LexiconLoaderTest] recomputes the collapsing rule over
 *   all 2M+ lines of `forms.tsv` and needs the 3 GB heap the `test` task grants. One such minion per
 *   PIT thread is enough to take a developer machine down, and this build killed itself proving it.
 * - [br.com.colman.palavramento.server.lexicon.RealLexiconCalibrationTest] asserts wall-clock budgets
 *   ("a full 4x4 solve takes under 50 ms"). Under PIT's instrumentation a timing assertion reports
 *   mutants as killed because the minion got slower, which is a score nobody can trust.
 *
 * Neither exclusion leaves a hole: `LexiconLoaderArtifactTest` covers [LexiconLoader] on its own, and
 * the filters and parsers the calibration spec touches have their own specs.
 */
val excludedFromMutationRun = listOf(
  "br.com.colman.palavramento.server.lexicon.LexiconLoaderTest",
  "br.com.colman.palavramento.server.lexicon.RealLexiconCalibrationTest",
).joinToString(",")

/** The one Postgres every PIT minion shares (ADR 0016), and how long the build waits for it. */
private val PitestPostgresContainer = "palavramento-pitest-postgres"
private val PitestPostgresImage = "postgres:16-alpine"
private val PitestPostgresReadyAttempts = 60
private val PitestPostgresReadyPollMillis = 1000L

/** Runs [command], returning its exit status and whatever it printed on either stream. */
private fun dockerCommand(vararg command: String): Pair<Int, String> {
  val process = ProcessBuilder(*command).redirectErrorStream(true).start()
  val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
  return process.waitFor() to output
}

/**
 * Starts the Postgres the minions share and returns its JDBC url.
 *
 * Testcontainers cannot do this job: it lives inside a JVM, and the JVMs that need the database are
 * the ones PIT forks, one per mutation unit. Each fork booting its own container is what made this
 * task a five hour run in CI. Durability is turned off because the server dies with the run, and
 * `fsync` is most of what a fork's `CREATE DATABASE` plus Flyway migration costs.
 */
private fun startPitestPostgres(): String {
  dockerCommand("docker", "rm", "-f", PitestPostgresContainer)
  val (startStatus, startOutput) = dockerCommand(
    "docker", "run", "-d", "--rm",
    "--name", PitestPostgresContainer,
    "-e", "POSTGRES_PASSWORD=postgres",
    "-e", "POSTGRES_USER=postgres",
    "-p", "127.0.0.1::5432",
    PitestPostgresImage,
    "-c", "fsync=off",
    "-c", "synchronous_commit=off",
    "-c", "full_page_writes=off",
    "-c", "max_connections=500",
  )
  check(startStatus == 0) { "Could not start the shared Postgres for pitest: $startOutput" }

  repeat(PitestPostgresReadyAttempts) {
    val (readyStatus, _) = dockerCommand("docker", "exec", PitestPostgresContainer, "pg_isready", "-U", "postgres")
    if (readyStatus == 0) {
      val (portStatus, portOutput) = dockerCommand("docker", "port", PitestPostgresContainer, "5432/tcp")
      check(portStatus == 0) { "Could not read the shared Postgres port: $portOutput" }
      val port = portOutput.lines().first().substringAfterLast(':')
      return "jdbc:postgresql://localhost:$port/postgres"
    }
    Thread.sleep(PitestPostgresReadyPollMillis)
  }
  error("The shared Postgres for pitest never became ready")
}

/** Stops the shared Postgres, whether the mutation run passed its threshold or not. */
val stopPitestPostgres = tasks.register("stopPitestPostgres") {
  description = "Stops the Postgres started for :server:pitest"
  doLast { dockerCommand("docker", "rm", "-f", PitestPostgresContainer) }
}

/**
 * Mutation testing, gated at 90% killed mutants (dossier §10, ADR 0016).
 *
 * Same command line entrypoint as `:domain` (the `info.solidsoft.pitest` plugin does not apply on
 * Gradle 9), with one difference this module forces: the `lexiconCompiler` source set is mutated too.
 *
 * Minion heap is deliberately modest. Every minion holds a Postgres container and, for the lexicon
 * specs, the 85 MB artifact, and PIT runs `--threads` of them at once, so a generous `-Xmx` here is
 * multiplied by the thread count and competes with the Gradle daemon's own 4 GB.
 */
val pitestTask = tasks.register<JavaExec>("pitest") {
  group = "verification"
  description = "Runs PIT mutation testing and fails below 90% killed mutants"

  val testSourceSet = sourceSets.test.get()
  val mutableCodePaths = sourceSets.main.get().output.classesDirs + sourceSets["lexiconCompiler"].output.classesDirs
  val codeUnderTest = testSourceSet.runtimeClasspath
  val sourceDirs = files("src/main/kotlin", "src/lexiconCompiler/kotlin")
  val reportDir = layout.buildDirectory.dir("reports/pitest")
  val classpathFile = layout.buildDirectory.file("tmp/pitest/classpath.txt")

  dependsOn(tasks.testClasses, compileLexicon)
  inputs.files(codeUnderTest).withPropertyName("codeUnderTest")
  inputs.files(sourceDirs).withPropertyName("sourceDirs")
  outputs.dir(reportDir).withPropertyName("report")

  javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
  mainClass = "org.pitest.mutationtest.commandline.MutationCoverageReport"
  // Test runtime first, so PIT's minions load exactly the jars the test task loads.
  classpath(codeUnderTest)
  classpath(pitest)
  // The launcher only coordinates: the work happens in the minions it forks.
  maxHeapSize = "1g"

  args(
    "--classPathFile=${classpathFile.get().asFile.absolutePath}",
    "--mutableCodePaths=${mutableCodePaths.joinToString(",") { it.absolutePath }}",
    "--sourceDirs=${sourceDirs.joinToString(",") { it.absolutePath }}",
    "--reportDir=${reportDir.get().asFile.absolutePath}",
    // Narrowing this from the command line is how you measure one class without waiting for the
    // whole module, e.g. -Ppalavramento.pitest.targetClasses=br.com.colman.palavramento.server.auth.*
    "--targetClasses=${providers.gradleProperty("palavramento.pitest.targetClasses").getOrElse("br.com.colman.palavramento.server.*")}",
    // PIT only hands specs matching this glob to the engine: every Kotest spec must be named *Test.
    "--targetTests=br.com.colman.palavramento.server.*Test",
    "--excludedClasses=$excludedFromMutation",
    "--excludedTestClasses=$excludedFromMutationRun",
    "--testPlugin=Kotest",
    "--mutationThreshold=90",
    // Testcontainers boots a Postgres per minion and the lexicon artifact is 85MB, so PIT's 4s
    // default timeout kills healthy minions long before they are actually stuck.
    "--timeoutConst=60000",
    "--outputFormats=HTML,XML",
    "--timestampedReports=false",
    "--failWhenNoMutations=false",
  )

  finalizedBy(stopPitestPostgres)

  doFirst {
    classpathFile.get().asFile.apply {
      parentFile.mkdirs()
      writeText(codeUnderTest.filter { it.exists() }.joinToString("\n") { it.absolutePath })
    }
    // Started here, not at configuration time: the container only has to exist while PIT runs, and
    // its port is different on every run, so it cannot be part of the task's inputs either.
    args("--jvmArgs=-Xmx1g,-Dpalavramento.test.postgres.url=${startPitestPostgres()}")
    // Added here so the core count of whichever machine runs the build stays out of the inputs.
    // Half the cores, not all of them: every PIT thread is a forked JVM holding up to 1 GB plus the
    // lexicon artifact, so this number is what decides whether the build finishes or the OOM killer
    // ends it. The Postgres they used to hold each is gone (see startPitestPostgres).
    args("--threads=${maxOf(1, Runtime.getRuntime().availableProcessors() / 2)}")
  }
}

// Mutation score is a build gate (dossier §10), not a weekly report.
tasks.check {
  dependsOn(pitestTask)
}
