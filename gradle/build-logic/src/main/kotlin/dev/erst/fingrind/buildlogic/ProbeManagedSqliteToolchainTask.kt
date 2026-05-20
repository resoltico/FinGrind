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
                operatingSystemId.get(),
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
                compilerExecutable,
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
        operatingSystemId: String,
        compilerExecutable: String,
        runCommand: (List<String>) -> CommandProbe,
    ): String {
        val rawProbeOutput =
            if (operatingSystemId == "windows") {
                requireProbeOutput(
                    "compiler target triple",
                    { ProbeAttempt(runCommand(listOf(compilerExecutable, "-dumpmachine"))) },
                    { ProbeAttempt(runCommand(listOf(compilerExecutable, "-print-target-triple"))) },
                    { ProbeAttempt(runCommand(listOf(compilerExecutable, "--print-target-triple"))) },
                    {
                        ProbeAttempt(
                            runCommand(listOf(compilerExecutable, "--version")),
                            acceptOutputOnFailure = true,
                        )
                    },
                    {
                        ProbeAttempt(
                            runCommand(listOf(compilerExecutable, "-v")),
                            acceptOutputOnFailure = true,
                        )
                    },
                    {
                        ProbeAttempt(
                            runCommand(listOf("cmd", "/c", "echo", "%VSCMD_ARG_TGT_ARCH%")),
                        )
                    },
                    {
                        ProbeAttempt(
                            runCommand(listOf("cmd", "/c", "echo", "%Platform%")),
                        )
                    },
                )
            } else {
                requireProbeOutput(
                    "compiler target triple",
                    { ProbeAttempt(runCommand(listOf(compilerExecutable, "-dumpmachine"))) },
                    { ProbeAttempt(runCommand(listOf(compilerExecutable, "-print-target-triple"))) },
                    { ProbeAttempt(runCommand(listOf(compilerExecutable, "--print-target-triple"))) },
                    {
                        ProbeAttempt(
                            runCommand(listOf(compilerExecutable, "--version")),
                            acceptOutputOnFailure = true,
                        )
                    },
                    {
                        ProbeAttempt(
                            runCommand(listOf(compilerExecutable, "-v")),
                            acceptOutputOnFailure = true,
                        )
                    },
                )
            }
        return normalizeTargetTriple(operatingSystemId, rawProbeOutput)
    }

    fun linkerVersion(
        operatingSystemId: String,
        runCommand: (List<String>) -> CommandProbe,
    ): String =
        when (operatingSystemId) {
            "windows" ->
                requireProbeOutput(
                    "linker version",
                    { ProbeAttempt(runCommand(listOf("link")), acceptOutputOnFailure = true) },
                    { ProbeAttempt(runCommand(listOf("link", "/?")), acceptOutputOnFailure = true) },
                )
            "macos" ->
                requireProbeOutput(
                    "linker version",
                    { ProbeAttempt(runCommand(listOf("ld", "-v"))) },
                    { ProbeAttempt(runCommand(listOf("ld", "-V"))) },
                )
            else ->
                requireProbeOutput(
                    "linker version",
                    { ProbeAttempt(runCommand(listOf("ld", "-v"))) },
                    { ProbeAttempt(runCommand(listOf("ld", "-V"))) },
                )
        }

    fun sdkOrSysroot(
        operatingSystemId: String,
        compilerExecutable: String,
        runCommand: (List<String>) -> CommandProbe,
    ): String =
        when (operatingSystemId) {
            "macos" ->
                requireProbeOutput(
                    "SDK path",
                    { ProbeAttempt(runCommand(listOf("xcrun", "--show-sdk-path"))) },
                )
            "windows" ->
                requireProbeOutput(
                    "Windows SDK path",
                    {
                        ProbeAttempt(
                            runCommand(
                                listOf(
                                    "cmd",
                                    "/c",
                                    "echo",
                                    "%WindowsSdkDir%%WindowsSDKVersion%",
                                ),
                            ),
                        )
                    },
                    {
                        ProbeAttempt(
                            runCommand(
                                listOf(
                                    "cmd",
                                    "/c",
                                    "echo",
                                    "%UniversalCRTSdkDir%%UCRTVersion%",
                                ),
                            ),
                        )
                    },
                )
            else ->
                normalizeSdkOrSysroot(
                    requireProbeOutput(
                        "sysroot or compiler search directories",
                        { ProbeAttempt(runCommand(listOf(compilerExecutable, "--print-sysroot"))) },
                        { ProbeAttempt(runCommand(listOf(compilerExecutable, "-print-sysroot"))) },
                        { ProbeAttempt(runCommand(listOf(compilerExecutable, "-print-search-dirs"))) },
                        {
                            ProbeAttempt(
                                runCommand(listOf(compilerExecutable, "-v")),
                                acceptOutputOnFailure = true,
                            )
                        },
                    ),
                )
        }

    private fun normalizeTargetTriple(
        operatingSystemId: String,
        rawProbeOutput: String,
    ): String {
        val normalizedLines =
            rawProbeOutput
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toList()
        normalizedLines.firstOrNull { looksLikeTargetTriple(it, operatingSystemId) }?.let { return it }
        val architectureToken =
            normalizedLines
                .asSequence()
                .mapNotNull(::extractArchitectureToken)
                .firstOrNull()
                ?: throw IllegalStateException(
                    "Failed to normalize the managed SQLite compiler target triple from probe output: $rawProbeOutput",
                )
        return when (operatingSystemId) {
            "macos" ->
                when (architectureToken) {
                    "aarch64", "arm64" -> "aarch64-apple-darwin"
                    "x86_64", "amd64" -> "x86_64-apple-darwin"
                    else ->
                        throw IllegalStateException(
                            "Unsupported managed SQLite compiler architecture $architectureToken for macos.",
                        )
                }
            "linux" ->
                when (architectureToken) {
                    "aarch64", "arm64" -> "aarch64-unknown-linux-gnu"
                    "x86_64", "amd64" -> "x86_64-unknown-linux-gnu"
                    else ->
                        throw IllegalStateException(
                            "Unsupported managed SQLite compiler architecture $architectureToken for linux.",
                        )
                }
            "windows" ->
                when (architectureToken) {
                    "aarch64", "arm64" -> "aarch64-pc-windows-msvc"
                    "x86_64", "amd64", "x64" -> "x86_64-pc-windows-msvc"
                    else ->
                        throw IllegalStateException(
                            "Unsupported managed SQLite compiler architecture $architectureToken for windows.",
                        )
                }
            else ->
                throw IllegalStateException(
                    "Unsupported managed SQLite operating system id for target-triple derivation: $operatingSystemId.",
                )
        }
    }

    private fun looksLikeTargetTriple(line: String, operatingSystemId: String): Boolean {
        val normalized = line.trim().lowercase()
        if (' ' in normalized) {
            return false
        }
        if (!normalized.contains("-")) {
            return false
        }
        return when (operatingSystemId) {
            "macos" -> "darwin" in normalized
            "linux" -> "linux" in normalized
            "windows" -> "windows" in normalized || "mingw" in normalized || "msvc" in normalized
            else -> false
        }
    }

    private fun extractArchitectureToken(line: String): String? {
        val normalized = line.trim().lowercase()
        return when {
            "aarch64" in normalized -> "aarch64"
            "arm64" in normalized -> "arm64"
            "x86_64" in normalized -> "x86_64"
            Regex("""\bx64\b""").containsMatchIn(normalized) -> "x64"
            "amd64" in normalized -> "amd64"
            else -> null
        }
    }

    private fun normalizeSdkOrSysroot(rawProbeOutput: String): String {
        val normalizedLines =
            rawProbeOutput
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toList()
        normalizedLines.firstOrNull { !it.startsWith("Configured with:") && !it.startsWith("Thread model:") }
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        throw IllegalStateException(
            "Failed to normalize the managed SQLite SDK/sysroot probe output: $rawProbeOutput",
        )
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
