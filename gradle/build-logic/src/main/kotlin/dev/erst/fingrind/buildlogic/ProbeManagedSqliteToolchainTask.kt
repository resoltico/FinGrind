package dev.erst.fingrind.buildlogic

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

@CacheableTask
abstract class ProbeManagedSqliteToolchainTask
    @Inject
    constructor(
        private val execOperations: ExecOperations,
    ) : DefaultTask() {
        @get:Input
        abstract val compiler: Property<String>

        @get:Input
        abstract val operatingSystemId: Property<String>

        @get:Input
        abstract val hostArchitecture: Property<String>

        @get:OutputFile
        abstract val outputFile: RegularFileProperty

        @TaskAction
    fun writeFingerprint() {
        val compilerCommand = compiler.get().trim()
        val compilerExecutable = resolveCompilerExecutable(compilerCommand, operatingSystemId.get())
        val compilerVersion =
            ManagedSqliteToolchainProbeSupport.compilerVersion(
                operatingSystemId.get(),
                compilerExecutable,
                ::runCommand,
            )
        val targetTriple =
            ManagedSqliteToolchainProbeSupport.targetTriple(
                compilerExecutable,
                ::runCommand,
            )
        val linkerVersion =
            ManagedSqliteToolchainProbeSupport.linkerVersion(
                operatingSystemId.get(),
                ::runCommand,
            )
        val sdkOrSysroot =
            ManagedSqliteToolchainProbeSupport.sdkOrSysroot(
                operatingSystemId.get(),
                ::runCommand,
            )
            outputFile.get().asFile.apply {
                parentFile.mkdirs()
                writeText(
                    buildString {
                        appendLine("{")
                        appendLine("  \"compilerCommand\": ${json(compilerCommand)},")
                        appendLine("  \"compilerExecutable\": ${json(compilerExecutable)},")
                        appendLine("  \"compilerVersion\": ${json(compilerVersion)},")
                        appendLine("  \"targetTriple\": ${json(targetTriple)},")
                        appendLine("  \"linkerVersion\": ${json(linkerVersion)},")
                        appendLine("  \"sdkOrSysroot\": ${json(sdkOrSysroot)},")
                        appendLine("  \"operatingSystemId\": ${json(operatingSystemId.get())},")
                        appendLine("  \"hostArchitecture\": ${json(hostArchitecture.get())}")
                        appendLine("}")
                    },
                    StandardCharsets.UTF_8,
                )
            }
        }

        private fun resolveCompilerExecutable(
            compilerCommand: String,
            activeOperatingSystemId: String,
        ): String {
            if (compilerCommand.contains("/") || compilerCommand.contains("\\")) {
                return java.io.File(compilerCommand).toPath().toAbsolutePath().normalize().toString()
            }
            val probe =
                if (activeOperatingSystemId == "windows") {
                    runCommand(listOf("where", compilerCommand))
                } else {
                    runCommand(listOf("sh", "-lc", "command -v -- \"\$1\"", "sh", compilerCommand))
                }
            return ManagedSqliteToolchainProbeSupport.compilerExecutable(probe)
                .lineSequence()
                .map { it.trim() }
                .firstOrNull(String::isNotBlank)
                ?: throw IllegalStateException(
                    "Failed to resolve the managed SQLite compiler executable for $compilerCommand.",
                )
        }

        private fun runCommand(commandLine: List<String>): CommandProbe {
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            val result =
                execOperations.exec {
                    commandLine(commandLine)
                    isIgnoreExitValue = true
                    standardOutput = stdout
                    errorOutput = stderr
                }
            return CommandProbe(
                result.exitValue,
                stdout.toString(StandardCharsets.UTF_8).trim(),
                stderr.toString(StandardCharsets.UTF_8).trim(),
            )
        }

        private fun json(value: String): String =
            buildString {
                append('"')
                value.forEach { character ->
                    when (character) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> append(character)
                    }
                }
                append('"')
            }
    }

internal object ManagedSqliteToolchainProbeSupport {
    fun compilerExecutable(probe: CommandProbe): String =
        requireProbeOutput(
            "compiler executable",
            { ProbeAttempt(probe) },
        )

    fun compilerVersion(
        operatingSystemId: String,
        compilerExecutable: String,
        runCommand: (List<String>) -> CommandProbe,
    ): String =
        when (operatingSystemId) {
            "windows" ->
                requireProbeOutput(
                    "compiler version",
                    {
                        ProbeAttempt(
                        runCommand(listOf(compilerExecutable, "/Bv")),
                        acceptOutputOnFailure = true,
                    )
                    },
                    {
                        ProbeAttempt(
                            runCommand(listOf(compilerExecutable)),
                            acceptOutputOnFailure = true,
                        )
                    },
                    {
                        ProbeAttempt(
                            runCommand(listOf(compilerExecutable, "/?")),
                            acceptOutputOnFailure = true,
                        )
                    },
                )
            else ->
                requireProbeOutput(
                    "compiler version",
                    { ProbeAttempt(runCommand(listOf(compilerExecutable, "--version"))) },
                    { ProbeAttempt(runCommand(listOf(compilerExecutable, "-v"))) },
                )
        }

    fun targetTriple(
        compilerExecutable: String,
        runCommand: (List<String>) -> CommandProbe,
    ): String =
        bestEffortProbeOutput(
            { ProbeAttempt(runCommand(listOf(compilerExecutable, "-dumpmachine"))) },
            fallback = "unavailable",
        )

    fun linkerVersion(
        operatingSystemId: String,
        runCommand: (List<String>) -> CommandProbe,
    ): String =
        when (operatingSystemId) {
            "windows" ->
                bestEffortProbeOutput(
                    { ProbeAttempt(runCommand(listOf("link")), acceptOutputOnFailure = true) },
                    { ProbeAttempt(runCommand(listOf("link", "/?")), acceptOutputOnFailure = true) },
                    fallback = "unavailable",
                )
            "macos" ->
                bestEffortProbeOutput(
                    { ProbeAttempt(runCommand(listOf("ld", "-v"))) },
                    { ProbeAttempt(runCommand(listOf("ld", "-V"))) },
                    fallback = "unavailable",
                )
            else ->
                bestEffortProbeOutput(
                    { ProbeAttempt(runCommand(listOf("ld", "-v"))) },
                    { ProbeAttempt(runCommand(listOf("ld", "-V"))) },
                    fallback = "unavailable",
                )
        }

    fun sdkOrSysroot(
        operatingSystemId: String,
        runCommand: (List<String>) -> CommandProbe,
    ): String =
        when (operatingSystemId) {
            "macos" ->
                bestEffortProbeOutput(
                    { ProbeAttempt(runCommand(listOf("xcrun", "--show-sdk-path"))) },
                    fallback = "unavailable",
                )
            else -> "unavailable"
        }

    private fun requireProbeOutput(label: String, vararg probes: () -> ProbeAttempt): String {
        val attempts = mutableListOf<ProbeAttempt>()
        probes.forEach { probeFactory ->
            val attempt = probeFactory()
            attempts += attempt
            attempt.acceptedOutput()?.let { return it }
        }
        throw IllegalStateException(
            "Failed to capture the managed SQLite $label. " +
                attempts.joinToString(separator = " | ") { probe -> probe.describe() },
        )
    }

    private fun bestEffortProbeOutput(vararg probes: () -> ProbeAttempt, fallback: String): String {
        probes.forEach { probeFactory ->
            probeFactory().acceptedOutput()?.let { return it }
        }
        return fallback
    }
}

internal data class ProbeAttempt(
    val probe: CommandProbe,
    val acceptOutputOnFailure: Boolean = false,
) {
    fun acceptedOutput(): String? {
        val combinedOutput = probe.combinedOutput()
        return when {
            probe.exitCode == 0 -> combinedOutput.takeIf(String::isNotEmpty)
            acceptOutputOnFailure -> combinedOutput.takeIf(String::isNotEmpty)
            else -> null
        }
    }

    fun describe(): String = "exit=${probe.exitCode} output=${probe.combinedOutput().ifEmpty { "<empty>" }}"
}

internal data class CommandProbe(val exitCode: Int, val stdout: String, val stderr: String) {
    fun combinedOutput(): String =
        listOf(stdout, stderr).filter(String::isNotEmpty).joinToString(separator = "\n")
}
