// Gradle init script to enable local development substitution for PollingEngine
// Usage (from the consumer project):
//   ./gradlew -I /Users/t0304iw/AndroidStudioProjects/PollingEngine/scripts/local-dev-pollingengine.init.gradle.kts \
//       -Ppollingengine.dir=/Users/t0304iw/AndroidStudioProjects/PollingEngine <task>
// Or set environment variable POLLINGENGINE_DIR to the absolute path of this repository and omit -P.
// This will substitute dependency in.androidplay:pollingengine with the local :pollingengine project via composite build.

import java.io.File

val defaultPath = "/Users/t0304iw/AndroidStudioProjects/PollingEngine"
val pollingEngineDirPath: String = (settings.findProperty("pollingengine.dir") as String?)
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: System.getenv("POLLINGENGINE_DIR")?.trim()?.takeIf { it.isNotEmpty() }
    ?: defaultPath

val pollingEngineDir = File(pollingEngineDirPath)

gradle.settingsEvaluated {
    // Only wire if the directory exists and looks like the PollingEngine repo
    val looksLikeRepo = pollingEngineDir.resolve("pollingengine/build.gradle.kts").exists()
    if (!pollingEngineDir.isDirectory || !looksLikeRepo) {
        println("[local-pollingengine] Skipping local includeBuild: directory not found or invalid at: $pollingEngineDirPath")
        return@settingsEvaluated
    }

    println("[local-pollingengine] Using local PollingEngine from: $pollingEngineDirPath")

    includeBuild(pollingEngineDir) {
        dependencySubstitution {
            // Substitute Maven coordinate with local project
            substitute(module("in.androidplay:pollingengine")).using(project(":pollingengine"))
        }
    }
}
