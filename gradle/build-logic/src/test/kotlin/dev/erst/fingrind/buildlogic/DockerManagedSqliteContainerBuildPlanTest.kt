package dev.erst.fingrind.buildlogic

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DockerManagedSqliteContainerBuildPlanTest {
    @Test
    fun dockerRunCommand_usesDirectContainerCompilationAndAnonymousFriendlyPulls() {
        val inputDirectory = Path.of("/tmp/input directory")
        val outputDirectory = Path.of("/tmp/output directory")
        val command =
            DockerManagedSqliteContainerBuildPlan.dockerRunCommand(
                platform = "linux/arm64",
                inputDirectory = inputDirectory,
                outputDirectory = outputDirectory,
                builderImage = "example/builder@sha256:abc123",
                buildBasePackage = "build-base",
                pythonPackage = "python3",
                sourceFileName = "sqlite3mc_amalgamation.c",
                architectureId = "aarch64",
                sqliteVersion = "3.53.2",
                requiredCompileOptions =
                    listOf(
                        "SQLITE_THREADSAFE=1",
                        "SQLITE_OMIT_LOAD_EXTENSION=1",
                        "SQLITE_TEMP_STORE=3",
                    ),
                requiresSecureMemorySupport = true,
                unixCompilerHardeningFlags = listOf("-fstack-protector-strong"),
                linuxLinkerHardeningFlags = listOf("-Wl,-z,relro", "-Wl,-z,now"),
            )

        assertEquals("docker", command[0])
        assertEquals("run", command[1])
        assertTrue(command.contains("--pull=always"))
        assertTrue(command.contains("--rm"))
        assertTrue(command.contains("--platform"))
        val inputMount =
            "type=bind,source=${inputDirectory.toAbsolutePath()},target=/input,readonly"
        assertTrue(
            command.contains(inputMount),
        )
        val outputMount = "type=bind,source=${outputDirectory.toAbsolutePath()},target=/output"
        assertTrue(
            command.contains(outputMount),
        )
        assertTrue(command.contains("example/builder@sha256:abc123"))
        assertFalse(command.contains("buildx"))

        val shellScript = command.last()
        assertTrue(shellScript.contains("apk add --no-cache 'build-base' 'python3'"))
        assertTrue(shellScript.contains("'/output/libsqlite3.so.0'"))
        assertTrue(shellScript.contains("'/input/sqlite3mc_amalgamation.c'"))
        assertTrue(shellScript.contains("'-I/input'"))
        assertTrue(shellScript.contains("\"buildEnvironment\": \"docker-run\""))
        assertTrue(shellScript.contains("\"builderImage\": \"example/builder@sha256:abc123\""))
    }
}
