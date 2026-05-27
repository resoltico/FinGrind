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
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

abstract class UvToolTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    private val requiredPythonMajorMinor: Pair<Int, Int>
        get() = parseRequiredPythonMajorMinor(requiredPythonVersion.get())

    @get:Optional
    @get:Input
    abstract val configuredPythonExecutable: Property<String>

    @get:Input
    abstract val uvExecutable: Property<String>

    @get:Input
    abstract val requiredPythonVersion: Property<String>

    @get:Input
    abstract val requiredUvVersion: Property<String>

    @get:Input
    abstract val toolCommand: Property<String>

    @get:Input
    abstract val toolArguments: ListProperty<String>

    @get:Input
    abstract val targetPaths: ListProperty<String>

    @get:Input
    abstract val bootstrapHint: Property<String>

    @get:Optional
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
    abstract val workingDirectory: DirectoryProperty

    @get:Internal
    abstract val uvCacheDirectory: DirectoryProperty

    @get:Optional
    @get:Input
    abstract val toolCacheEnvironmentVariable: Property<String>

    @get:Internal
    abstract val toolCacheDirectory: DirectoryProperty

    @TaskAction
    fun runTool() {
        if (sourceFiles.files.isEmpty()) {
            logger.info("Skipping {} because no tool inputs were found.", name)
            return
        }
        uvCacheDirectory.get().asFile.mkdirs()
        if (toolCacheEnvironmentVariable.isPresent) {
            toolCacheDirectory.get().asFile.mkdirs()
        }
        val pythonExecutable = resolvePythonExecutable()
        verifyPythonVersion(pythonExecutable)
        verifyUvAvailability()
        executeTool(pythonExecutable)
    }

    private fun resolvePythonExecutable(): String {
        val configuredExecutable =
            configuredPythonExecutable.orNull?.trim()?.takeIf(String::isNotBlank)
        if (configuredExecutable != null) {
            return configuredExecutable
        }
        return selectExactPythonExecutable(
            requiredPythonVersion = requiredPythonVersion.get(),
            osName = System.getProperty("os.name"),
            probeVersion = ::probePythonMajorMinor,
            findUvManagedPythonExecutable = ::findUvManagedPythonExecutable,
        )
            ?: throw GradleException(
                "FinGrind requires Python ${requiredPythonVersion.get()} for repo-owned Python tooling. " +
                    "Install or expose an exact Python ${requiredPythonVersion.get()} interpreter, or set -PfingrindPythonExecutable to one exact interpreter path. " +
                    bootstrapHint.get(),
            )
    }

    private fun probePythonMajorMinor(command: String): Pair<Int, Int>? {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val result =
            execOperations.exec {
                executable = command
                args("--version")
                isIgnoreExitValue = true
                configureSharedEnvironment(this)
                standardOutput = stdout
                errorOutput = stderr
            }
        if (result.exitValue != 0) {
            return null
        }
        return parsePythonMajorMinor(normalizedOutput(stdout, stderr))
    }

    private fun findUvManagedPythonExecutable(requiredPythonVersion: String): String? {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val result =
            execOperations.exec {
                executable = uvExecutable.get()
                args("python", "find", requiredPythonVersion)
                isIgnoreExitValue = true
                configurePythonLocatorEnvironment(this)
                standardOutput = stdout
                errorOutput = stderr
            }
        if (result.exitValue != 0) {
            return null
        }
        return normalizedOutput(stdout, stderr)
            .lineSequence()
            .map(String::trim)
            .firstOrNull(String::isNotBlank)
    }

    private fun verifyPythonVersion(pythonExecutable: String) {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val result =
            execOperations.exec {
                executable = pythonExecutable
                args("--version")
                isIgnoreExitValue = true
                configureSharedEnvironment(this)
                standardOutput = stdout
                errorOutput = stderr
            }
        val detectedOutput = normalizedOutput(stdout, stderr)
        val detectedVersion = parsePythonVersionBanner(detectedOutput)
        val detectedMajorMinor = parsePythonMajorMinor(detectedOutput)
        if (
            result.exitValue != 0 ||
                detectedVersion == null ||
                detectedMajorMinor == null ||
                !pythonVersionMatchesRequirement(detectedMajorMinor, requiredPythonMajorMinor)
        ) {
            throw GradleException(
                buildString {
                    append("Python tool tasks require exactly Python ")
                    append(requiredPythonVersion.get())
                    append(" but ")
                    append(pythonExecutable)
                    append(" reports ")
                    append(
                        detectedVersion
                            ?: detectedOutput.lineSequence().firstOrNull().orEmpty().ifBlank { "an unsupported version" },
                    )
                    append(". ")
                    append(bootstrapHint.get())
                },
            )
        }
    }

    private fun verifyUvAvailability() {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val result =
            execOperations.exec {
                executable = uvExecutable.get()
                args("--version")
                isIgnoreExitValue = true
                configureSharedEnvironment(this)
                standardOutput = stdout
                errorOutput = stderr
            }
        val normalizedOutput = normalizedOutput(stdout, stderr)
        val expectedVersionPrefix = "uv ${requiredUvVersion.get()}"
        if (
            result.exitValue != 0 ||
                !normalizedOutput.lineSequence().any { it.trim().startsWith(expectedVersionPrefix) }
        ) {
            throw GradleException(
                buildString {
                    append("FinGrind requires uv ")
                    append(requiredUvVersion.get())
                    append(" for repo-owned Python tooling, but ")
                    append(uvExecutable.get())
                    append(" reports ")
                    append(if (normalizedOutput.isBlank()) "no usable uv launcher" else normalizedOutput)
                    append(". ")
                    append(bootstrapHint.get())
                },
            )
        }
    }

    private fun executeTool(pythonExecutable: String) {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val command = commandLine(pythonExecutable)
        val result =
            execOperations.exec {
                workingDir = workingDirectory.get().asFile
                executable = uvExecutable.get()
                args(command)
                isIgnoreExitValue = true
                configureSharedEnvironment(this)
                standardOutput = stdout
                errorOutput = stderr
            }
        if (result.exitValue != 0) {
            throw GradleException(
                buildString {
                    append(toolCommand.get())
                    append(" command failed with exit code ")
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

    private fun commandLine(pythonExecutable: String): List<String> =
        buildList {
            add("tool")
            add("run")
            add("--isolated")
            add("--python")
            add(pythonExecutable)
            add("--with-requirements")
            add(requirementsFile.get().asFile.absolutePath)
            add(toolCommand.get())
            addAll(toolArguments.get())
            if (configFile.isPresent) {
                add("--config")
                add(configFile.get().asFile.absolutePath)
            }
            addAll(targetPaths.get())
        }

    private fun configureSharedEnvironment(spec: org.gradle.process.ExecSpec) {
        spec.environment("PYTHONDONTWRITEBYTECODE", "1")
        spec.environment("UV_CACHE_DIR", uvCacheDirectory.get().asFile.absolutePath)
        spec.environment("UV_NO_MANAGED_PYTHON", "1")
        spec.environment("UV_PYTHON_DOWNLOADS", "never")
        spec.environment("UV_NO_PROGRESS", "1")
        if (toolCacheEnvironmentVariable.isPresent) {
            spec.environment(
                toolCacheEnvironmentVariable.get(),
                toolCacheDirectory.get().asFile.absolutePath,
            )
        }
    }

    private fun configurePythonLocatorEnvironment(spec: org.gradle.process.ExecSpec) {
        spec.environment("UV_CACHE_DIR", uvCacheDirectory.get().asFile.absolutePath)
        spec.environment("UV_PYTHON_DOWNLOADS", "never")
        spec.environment("UV_NO_PROGRESS", "1")
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

private val PYTHON_VERSION_BANNER_REGEX = Regex("""\bPython\s+(\d+)\.(\d+)(?:\.\d+)?\b""")

internal fun parsePythonVersionBanner(output: String): String? =
    PYTHON_VERSION_BANNER_REGEX.find(output)?.value?.trim()

internal fun parsePythonMajorMinor(output: String): Pair<Int, Int>? =
    PYTHON_VERSION_BANNER_REGEX.find(output)?.destructured?.let { (major, minor) ->
        major.toInt() to minor.toInt()
    }

internal fun parseRequiredPythonMajorMinor(requiredVersion: String): Pair<Int, Int> {
    val components = requiredVersion.split('.', limit = 3)
    require(components.size >= 2) {
        "Required Python version must contain major and minor components: $requiredVersion"
    }
    return components[0].toInt() to components[1].toInt()
}
