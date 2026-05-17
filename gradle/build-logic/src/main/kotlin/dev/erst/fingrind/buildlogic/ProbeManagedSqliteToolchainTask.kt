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
                requireProbeOutput(
                    "compiler version",
                    runCommand(listOf(compilerExecutable, "--version")),
                    runCommand(listOf(compilerExecutable, "-v")),
                )
            val targetTriple =
                bestEffortProbeOutput(
                    runCommand(listOf(compilerExecutable, "-dumpmachine")),
                    fallback = "unavailable",
                )
            val linkerVersion =
                when (operatingSystemId.get()) {
                    "windows" ->
                        bestEffortProbeOutput(
                            runCommand(listOf("link")),
                            fallback = "unavailable",
                        )
                    "macos" ->
                        bestEffortProbeOutput(
                            runCommand(listOf("ld", "-v")),
                            runCommand(listOf("ld", "-V")),
                            fallback = "unavailable",
                        )
                    else ->
                        bestEffortProbeOutput(
                            runCommand(listOf("ld", "-v")),
                            runCommand(listOf("ld", "-V")),
                            fallback = "unavailable",
                        )
                }
            val sdkOrSysroot =
                when (operatingSystemId.get()) {
                    "macos" ->
                        bestEffortProbeOutput(
                            runCommand(listOf("xcrun", "--show-sdk-path")),
                            fallback = "unavailable",
                        )
                    else -> "unavailable"
                }
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
            return requireProbeOutput("compiler executable", probe)
                .lineSequence()
                .map(String::trim)
                .firstOrNull(String::isNotEmpty)
                ?: throw IllegalStateException(
                    "Failed to resolve the managed SQLite compiler executable for $compilerCommand.",
                )
        }

        private fun requireProbeOutput(label: String, vararg probes: CommandProbe): String =
            probes.firstNotNullOfOrNull(CommandProbe::successfulOutput)
                ?: throw IllegalStateException(
                    "Failed to capture the managed SQLite $label. " +
                        probes.joinToString(separator = " | ") { probe -> probe.describe() },
                )

        private fun bestEffortProbeOutput(vararg probes: CommandProbe, fallback: String): String =
            probes.firstNotNullOfOrNull(CommandProbe::successfulOutput) ?: fallback

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

        private data class CommandProbe(val exitCode: Int, val stdout: String, val stderr: String) {
            fun successfulOutput(): String? =
                if (exitCode == 0) {
                    combinedOutput().takeIf(String::isNotEmpty)
                } else {
                    null
                }

            fun describe(): String = "exit=$exitCode output=${combinedOutput().ifEmpty { "<empty>" }}"

            private fun combinedOutput(): String =
                listOf(stdout, stderr).filter(String::isNotEmpty).joinToString(separator = "\n")
        }
    }
