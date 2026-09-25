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
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.triggers.Cron
import io.github.typesafegithub.workflows.domain.triggers.Schedule
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow

workflow(
  name = "Mutation",
  // Weekly, and by hand. Mutation testing left `check` (ADR 0023): it measures the tests, and a measure
  // taken once a week is enough to see the trend without making every push wait forty minutes for it.
  // Mondays 06:00 UTC, the middle of the night in Brazil.
  on = listOf(Schedule(listOf(Cron(minute = "0", hour = "6", dayWeek = "1"))), WorkflowDispatch()),
  // Writing the badge means pushing to the `badges` branch.
  permissions = mapOf(Permission.Contents to Mode.Write),
  sourceFile = __FILE__,
) {
  job(id = "mutation", runsOn = RunnerType.UbuntuLatest) {
    uses(name = "Checkout", action = Checkout())
    uses(name = "Setup JDK", action = SetupJava(javaVersion = "21", distribution = SetupJava.Distribution.Temurin))
    uses(name = "Setup Gradle", action = ActionsSetupGradle())

    // All three modules, and all of them even when one falls under its threshold: a red module still has
    // a number worth recording, and the job still ends red so the drop is seen.
    run(name = "Mutation", command = "./gradlew pitest --continue")

    run(
      name = "Test strength",
      // pipefail: Actions runs `bash -e` without it, and `tee` would otherwise turn a failed script green.
      command = "set -o pipefail; kotlin tools/mutation-strength.main.kts --badge mutation.json | tee -a \"${'$'}GITHUB_STEP_SUMMARY\"",
      condition = "always()",
    )

    // An orphan branch holding one file, rewritten each run: the badge needs a public URL, and a weekly
    // robot commit on main would bury the history people actually read.
    run(
      name = "Publish badge",
      command = """
        test -f mutation.json || exit 0
        dir="${'$'}(mktemp -d)"
        cp mutation.json "${'$'}dir/"
        cd "${'$'}dir"
        git init -q -b badges
        git add mutation.json
        git -c user.name='github-actions[bot]' \
          -c user.email='41898282+github-actions[bot]@users.noreply.github.com' \
          commit -q -m 'Mutation test strength'
        git push -q -f "https://x-access-token:${'$'}GITHUB_TOKEN@github.com/${'$'}GITHUB_REPOSITORY.git" badges
      """.trimIndent(),
      env = mapOf("GITHUB_TOKEN" to "\${{ secrets.GITHUB_TOKEN }}"),
      condition = "always()",
    )

    uses(
      name = "Upload reports",
      action = UploadArtifact(name = "pitest-reports", path = listOf("**/build/reports/pitest")),
      condition = "always()",
    )
  }
}
