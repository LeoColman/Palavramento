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
val frequencyFile = lexiconSourceDir.file("pt_br_50k.txt")

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
