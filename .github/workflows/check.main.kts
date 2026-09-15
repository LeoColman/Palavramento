#!/usr/bin/env kotlin
@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:4.0.0")

@file:Repository("https://bindings.krzeminski.it")
@file:DependsOn("actions:checkout:v4")
@file:DependsOn("actions:setup-java:v5")
@file:DependsOn("actions:upload-artifact:v4")
@file:DependsOn("gradle:actions__setup-gradle:v4")

import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.actions.SetupJava
import io.github.typesafegithub.workflows.actions.actions.UploadArtifact
import io.github.typesafegithub.workflows.actions.gradle.ActionsSetupGradle
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.dsl.workflow

workflow(
  name = "Check",
  on = listOf(Push(branches = listOf("main")), PullRequest()),
  sourceFile = __FILE__,
) {
  job(id = "check", runsOn = RunnerType.UbuntuLatest) {
    uses(name = "Checkout", action = Checkout())
    // 21 runs the Gradle daemon (gradle/gradle-daemon-jvm.properties); foojay fetches the 17 toolchain.
    uses(name = "Setup JDK", action = SetupJava(javaVersion = "21", distribution = SetupJava.Distribution.Temurin))
    uses(name = "Setup Gradle", action = ActionsSetupGradle())
    // Tests, detekt, Android lint and the mutation gate on :domain. Testcontainers uses the runner's Docker.
    run(name = "Check", command = "./gradlew check")
    uses(
      name = "Upload reports",
      action = UploadArtifact(name = "reports", path = listOf("**/build/reports")),
      // A failed run is exactly when the reports are worth reading.
      condition = "always()",
    )
  }
}
