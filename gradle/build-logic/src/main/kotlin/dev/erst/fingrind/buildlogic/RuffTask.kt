package dev.erst.fingrind.buildlogic

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

abstract class RuffTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:Input
    abstract val pythonExecutable: Property<String>

    @get:Input
    abstract val ruffArguments: ListProperty<String>

    @get:Input
    abstract val targetPaths: ListProperty<String>

    @get:Input
    abstract val installHint: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val configFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val requirementsFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Internal
    abstract val pythonPycacheDirectory: DirectoryProperty

    @get:Internal
    abstract val ruffCacheDirectory: DirectoryProperty

    @get:Internal
    abstract val workingDirectory: DirectoryProperty

    @TaskAction
    fun runRuff() {
        if (sourceFiles.files.isEmpty()) {
            logger.info("Skipping {} because no Python script inputs were found.", name)
            return
        }
        pythonPycacheDirectory.get().asFile.mkdirs()
        ruffCacheDirectory.get().asFile.mkdirs()
        verifyRuffInstalled()
        executeRuff()
    }

    private fun verifyRuffInstalled() {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val result =
            execOperations.exec {
                executable = pythonExecutable.get()
                args(
                    "-c",
                    "import importlib.util, sys; sys.exit(0 if importlib.util.find_spec('ruff') else 1)",
                )
                isIgnoreExitValue = true
                environment("PYTHONDONTWRITEBYTECODE", "1")
                environment("PYTHONPYCACHEPREFIX", pythonPycacheDirectory.get().asFile.absolutePath)
                standardOutput = stdout
                errorOutput = stderr
            }
        if (result.exitValue != 0) {
            throw GradleException(
                buildString {
                    append("Ruff is not available via ")
                    append(pythonExecutable.get())
                    append(". ")
                    append(installHint.get())
                    val output = normalizedOutput(stdout, stderr)
                    if (output.isNotEmpty()) {
                        append(System.lineSeparator())
                        append(output)
                    }
                },
            )
        }
    }

    private fun executeRuff() {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val command =
            listOf(
                pythonExecutable.get(),
                "-m",
                "ruff",
                "--config",
                configFile.get().asFile.absolutePath,
            ) + ruffArguments.get() + targetPaths.get()
        val result =
            execOperations.exec {
                workingDir = workingDirectory.get().asFile
                executable = pythonExecutable.get()
                args("-m", "ruff", "--config", configFile.get().asFile.absolutePath)
                args(ruffArguments.get())
                args(targetPaths.get())
                isIgnoreExitValue = true
                environment("PYTHONDONTWRITEBYTECODE", "1")
                environment("PYTHONPYCACHEPREFIX", pythonPycacheDirectory.get().asFile.absolutePath)
                environment("RUFF_CACHE_DIR", ruffCacheDirectory.get().asFile.absolutePath)
                standardOutput = stdout
                errorOutput = stderr
            }
        if (result.exitValue != 0) {
            throw GradleException(
                buildString {
                    append("Ruff command failed with exit code ")
                    append(result.exitValue)
                    append(": ")
                    append(command.joinToString(" "))
                    val output = normalizedOutput(stdout, stderr)
                    if (output.isNotEmpty()) {
                        append(System.lineSeparator())
                        append(output)
                    }
                },
            )
        }
    }

    private fun normalizedOutput(
        stdout: ByteArrayOutputStream,
        stderr: ByteArrayOutputStream,
    ): String =
        buildString {
            append(stdout.toString(StandardCharsets.UTF_8))
            append(stderr.toString(StandardCharsets.UTF_8))
        }.trim()
}
