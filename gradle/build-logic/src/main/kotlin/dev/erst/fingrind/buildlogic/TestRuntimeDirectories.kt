package dev.erst.fingrind.buildlogic

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.util.Comparator
import org.gradle.api.tasks.JavaExec

private const val testRuntimeDirectoryName = "fingrind-test-runtime"

/** Defines and resets a task-owned home, temporary, and publication-state root for test processes. */
internal data class TestRuntimeDirectories(
    val privateRoot: File,
    val systemProperties: Map<String, String>,
    val environment: Map<String, String>,
)

internal fun selectTestRuntimeDirectories(
    configuredPrivateRoot: File?,
    defaultTemporaryDirectory: File,
): TestRuntimeDirectories {
    val privateRoot =
        (configuredPrivateRoot ?: defaultTemporaryDirectory).resolve(testRuntimeDirectoryName)
    val stateHome = privateRoot.resolve(".local").resolve("state")
    val localAppData = privateRoot.resolve("AppData").resolve("Local")
    return TestRuntimeDirectories(
        privateRoot = privateRoot,
        systemProperties =
            mapOf(
                "java.io.tmpdir" to privateRoot.absolutePath,
                "user.home" to privateRoot.absolutePath,
            ),
        environment =
            mapOf(
                "XDG_STATE_HOME" to stateHome.absolutePath,
                "LOCALAPPDATA" to localAppData.absolutePath,
            ),
    )
}

/** Deletes only the task-owned descendant so a configured private parent keeps its security policy. */
internal fun resetTestRuntimeDirectories(directories: TestRuntimeDirectories) {
    val root = directories.privateRoot.toPath()
    if (Files.exists(root, NOFOLLOW_LINKS)) {
        Files.walk(root).use { entries ->
            entries.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }
    Files.createDirectories(root)
}

/** Applies task-owned state isolation to a local Jazzer Java-execution task. */
internal fun JavaExec.configureTestRuntimeState() {
    val directories = selectTestRuntimeDirectories(null, temporaryDir)
    directories.systemProperties.forEach(::systemProperty)
    directories.environment.forEach(::environment)
    outputs.dir(directories.privateRoot)
    doFirst {
        resetTestRuntimeDirectories(directories)
    }
}
