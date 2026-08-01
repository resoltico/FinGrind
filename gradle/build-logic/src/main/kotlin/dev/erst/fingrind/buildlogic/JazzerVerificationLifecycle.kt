package dev.erst.fingrind.buildlogic

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

internal fun Project.configureJazzerVerificationLifecycle(
    fuzzTasks: List<TaskProvider<out org.gradle.api.Task>>,
    regressionTasks: List<TaskProvider<out org.gradle.api.Task>>,
    jazzerRegression: TaskProvider<out org.gradle.api.Task>,
    sqliteWhiteBoxTestPatch: TaskProvider<Sync>,
    executorWhiteBoxTestPatch: TaskProvider<Sync>,
    sqliteWhiteBoxTestPatchDirectory: Provider<Directory>,
    executorWhiteBoxTestPatchDirectory: Provider<Directory>,
) {
    tasks.named<Test>("test") {
        description = "Runs deterministic Jazzer replay and harness tests."
        group = "verification"
        useJUnitPlatform()
        maxParallelForks = 1
        dependsOn(sqliteWhiteBoxTestPatch)
        dependsOn(executorWhiteBoxTestPatch)
        enableUnnamedNativeAccess()
        enableCoreNamedNativeAccess()
        enableSqliteNamedNativeAccess()
        allowSunMiscUnsafeMemoryAccess()
        disableClassDataSharing()
        patchModule(
            "dev.erst.fingrind.sqlite",
            files(sqliteWhiteBoxTestPatchDirectory),
        )
        patchModule(
            "dev.erst.fingrind.executor",
            files(executorWhiteBoxTestPatchDirectory),
        )
        addReads("dev.erst.fingrind.sqlite", "ALL-UNNAMED")
        addOpens("dev.erst.fingrind.sqlite", "dev.erst.fingrind.sqlite", "ALL-UNNAMED")
        systemProperty(jazzerTestProjectRootProperty, layout.projectDirectory.asFile.absolutePath)
        doFirst {
            addTestListener(JazzerDeterministicTestPulseListener())
        }
    }

    tasks.named("check") {
        dependsOn(jazzerRegression)
    }

    tasks.register("fuzzAllLocal") {
        description = "Runs all active local-only FinGrind fuzzing tasks."
        group = "verification"
        dependsOn(fuzzTasks)
    }

    fuzzTasks.windowed(size = 2, step = 1, partialWindows = false).forEach { (first, second) ->
        second.configure {
            mustRunAfter(first)
        }
    }

    val jazzerDeterministicTests = tasks.named<Test>("test")
    regressionTasks.windowed(size = 2, step = 1, partialWindows = false).forEach { (first, second) ->
        second.configure {
            mustRunAfter(first)
            mustRunAfter(jazzerDeterministicTests)
        }
    }
    regressionTasks.firstOrNull()?.configure {
        mustRunAfter(jazzerDeterministicTests)
    }

}
