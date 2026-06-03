package dev.erst.fingrind.buildlogic

import java.io.File
import java.security.MessageDigest
import java.util.HexFormat

internal object ManagedSqliteArtifactSupport {
    fun unixCompilerDefines(
        requiredCompileOptions: List<String>,
        requiresSecureMemorySupport: Boolean,
    ): List<String> =
        compilerDefines(requiredCompileOptions, requiresSecureMemorySupport) { option ->
            "-D$option"
        }

    fun windowsCompilerDefines(
        requiredCompileOptions: List<String>,
        requiresSecureMemorySupport: Boolean,
    ): List<String> =
        compilerDefines(requiredCompileOptions, requiresSecureMemorySupport) { option ->
            "/D$option"
        }

    fun writeChecksumFile(
        outputLibraryFile: File,
        checksumOutputFile: File,
    ) {
        val digest = MessageDigest.getInstance("SHA-256").digest(outputLibraryFile.readBytes())
        checksumOutputFile.parentFile.mkdirs()
        checksumOutputFile.writeText(
            HexFormat.of().formatHex(digest) +
                "  " +
                outputLibraryFile.name +
                System.lineSeparator(),
        )
    }

    fun writeBuildContractFile(
        buildContractOutputFile: File,
        sqliteVersion: String,
        operatingSystemId: String,
        requiredCompileOptions: List<String>,
        forbiddenCompileOptions: List<String>,
        requiresSecureMemorySupport: Boolean,
    ) {
        buildContractOutputFile.parentFile.mkdirs()
        buildContractOutputFile.writeText(
            buildString {
                appendLine("{")
                appendLine("  \"sqliteVersion\": ${json(sqliteVersion)},")
                appendLine("  \"operatingSystemId\": ${json(operatingSystemId)},")
                appendLine("  \"requiredCompileOptions\": ${jsonArray(requiredCompileOptions)},")
                appendLine("  \"forbiddenCompileOptions\": ${jsonArray(forbiddenCompileOptions)},")
                appendLine(
                    "  \"requiresSecureMemorySupport\": $requiresSecureMemorySupport",
                )
                appendLine("}")
            },
        )
    }

    private fun compilerDefines(
        requiredCompileOptions: List<String>,
        requiresSecureMemorySupport: Boolean,
        flagBuilder: (String) -> String,
    ): List<String> =
        requiredCompileOptions.map { option ->
            val normalized = option.trim()
            require(normalized.isNotEmpty()) {
                "Managed SQLite compile options must not be blank."
            }
            val sqliteOption =
                if (normalized.contains("=")) {
                    normalized
                } else {
                    "$normalized=1"
                }
            flagBuilder("SQLITE_$sqliteOption")
        } + listOfNotNull(
            if (requiresSecureMemorySupport) {
                flagBuilder("SQLITE3MC_SECURE_MEMORY=1")
            } else {
                null
            },
        )

    private fun jsonArray(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]") { value -> json(value) }

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
