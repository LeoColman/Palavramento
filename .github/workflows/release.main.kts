#!/usr/bin/env kotlin
@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:4.0.0")

@file:Repository("https://bindings.krzeminski.it")
@file:DependsOn("actions:checkout:v4")
@file:DependsOn("actions:setup-java:v5")
@file:DependsOn("gradle:actions__setup-gradle:v4")

import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.actions.SetupJava
import io.github.typesafegithub.workflows.actions.gradle.ActionsSetupGradle
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow

// Pinned: a release is not the moment to find out what changed in fastlane overnight.
val FastlaneVersion = "2.240.1"

workflow(
  name = "Release",
  // Never on a push to main. This workflow publishes live on the production track, so it only
  // ever starts because someone tagged a version or pressed the button (ADR 0022).
  on = listOf(Push(tags = listOf("v*")), WorkflowDispatch()),
  sourceFile = __FILE__,
) {
  // Publishing without the gates would make `check` advisory on the one push where it matters.
  val check = job(id = "check", runsOn = RunnerType.UbuntuLatest) {
    uses(name = "Checkout", action = Checkout())
    uses(name = "Setup JDK", action = SetupJava(javaVersion = "21", distribution = SetupJava.Distribution.Temurin))
    uses(name = "Setup Gradle", action = ActionsSetupGradle())
    run(name = "Check", command = "./gradlew check")
  }

  job(id = "release", runsOn = RunnerType.UbuntuLatest, needs = listOf(check)) {
    uses(name = "Checkout", action = Checkout())
    uses(name = "Setup JDK", action = SetupJava(javaVersion = "21", distribution = SetupJava.Distribution.Temurin))
    uses(name = "Setup Gradle", action = ActionsSetupGradle())

    // Ruby ships with the runner image; fastlane is pinned rather than resolved fresh.
    run(name = "Install fastlane", command = "gem install fastlane -v $FastlaneVersion --no-document")

    // The upload key, and only the upload key. The GPG key that opens everything else git-secret
    // holds stays off CI on purpose (ADR 0017, ADR 0022).
    run(
      name = "Write the signing key",
      command = """
        printf '%s' "${'$'}RELEASE_KEYSTORE_BASE64" | base64 -d > app/palavramento-release.jks
        cat > keystore.properties <<'PROPERTIES'
        storeFile=app/palavramento-release.jks
        keyAlias=palavramento
        PROPERTIES
        printf 'storePassword=%s\nkeyPassword=%s\n' \
          "${'$'}RELEASE_KEYSTORE_PASSWORD" "${'$'}RELEASE_KEYSTORE_PASSWORD" >> keystore.properties
      """.trimIndent(),
      env = mapOf(
        "RELEASE_KEYSTORE_BASE64" to "\${{ secrets.RELEASE_KEYSTORE_BASE64 }}",
        "RELEASE_KEYSTORE_PASSWORD" to "\${{ secrets.RELEASE_KEYSTORE_PASSWORD }}",
      ),
    )

    run(
      name = "Write the Play service account",
      command = "printf '%s' \"${'$'}PLAY_SERVICE_ACCOUNT_JSON\" > fastlane/play-service-account.json",
      env = mapOf(
        "PLAY_SERVICE_ACCOUNT_JSON" to "\${{ secrets.PLAY_SERVICE_ACCOUNT_JSON }}",
      ),
    )

    run(
      name = "Publish",
      command = "fastlane android release",
      env = mapOf(
        "SUPPLY_JSON_KEY" to "fastlane/play-service-account.json",
        // Only a tag names a version. Pressing the button by hand leaves this empty, and the
        // Fastfile then trusts app/build.gradle.kts on its own.
        "RELEASE_TAG" to "\${{ startsWith(github.ref, 'refs/tags/') && github.ref_name || '' }}",
      ),
    )
  }
}
