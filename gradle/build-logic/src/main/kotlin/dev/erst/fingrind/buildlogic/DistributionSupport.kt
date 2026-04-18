package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

object DistributionSupport {
    private const val PUBLIC_DISTRIBUTION_CONTRACT_PATH =
        "contract/src/main/resources/dev/erst/fingrind/contract/protocol/public-distribution-contract.properties"
    private const val SUPPORTED_BUNDLE_TARGETS_KEY = "supportedPublicCliBundleTargets"
    private const val UNSUPPORTED_OPERATING_SYSTEMS_KEY = "unsupportedPublicCliOperatingSystems"

    fun publicCliBundleTargets(projectRootDirectory: Path): List<String> =
        loadPublicDistributionContract(projectRootDirectory).supportedPublicCliBundleTargets

    fun unsupportedPublicCliOperatingSystems(projectRootDirectory: Path): List<String> =
        loadPublicDistributionContract(projectRootDirectory).unsupportedOperatingSystems

    fun operatingSystemId(osName: String = System.getProperty("os.name", "")): String {
        val operatingSystem = osName.lowercase()
        if (operatingSystem.contains("mac")) {
            return "macos"
        }
        if (operatingSystem.contains("linux")) {
            return "linux"
        }
        if (operatingSystem.contains("windows")) {
            return "windows"
        }
        throw IllegalStateException("FinGrind bundles currently support macOS, Linux, and Windows only.")
    }

    fun architectureId(architecture: String = System.getProperty("os.arch", "unknown")): String =
        when (architecture.lowercase()) {
            "arm64", "aarch64" -> "aarch64"
            "amd64", "x86_64", "x64" -> "x86_64"
            else -> architecture.lowercase().replace(Regex("[^a-z0-9]+"), "-")
        }

    fun hostClassifier(
        osName: String = System.getProperty("os.name", ""),
        architecture: String = System.getProperty("os.arch", "unknown"),
    ): String = operatingSystemId(osName) + "-" + architectureId(architecture)

    fun libraryFileNameForOperatingSystemId(operatingSystemId: String): String =
        when (operatingSystemId) {
            "macos" -> "libsqlite3.dylib"
            "linux" -> "libsqlite3.so.0"
            "windows" -> "sqlite3.dll"
            else -> throw IllegalStateException("Unsupported operating system id: $operatingSystemId")
        }

    fun archiveExtensionForOperatingSystemId(operatingSystemId: String): String =
        when (operatingSystemId) {
            "macos", "linux" -> "tar.gz"
            "windows" -> "zip"
            else -> throw IllegalStateException("Unsupported operating system id: $operatingSystemId")
        }

    fun launcherPathForOperatingSystemId(operatingSystemId: String): String =
        when (operatingSystemId) {
            "macos", "linux" -> "bin/fingrind"
            "windows" -> "bin/fingrind.cmd"
            else -> throw IllegalStateException("Unsupported operating system id: $operatingSystemId")
        }

    fun launcherCommandForOperatingSystemId(operatingSystemId: String): String =
        when (operatingSystemId) {
            "macos", "linux" -> "./bin/fingrind"
            "windows" -> ".\\bin\\fingrind.cmd"
            else -> throw IllegalStateException("Unsupported operating system id: $operatingSystemId")
        }

    fun jsonString(value: String): String =
        buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000c' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
            append('"')
        }

    fun jsonStringArray(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]") { value -> jsonString(value) }

    fun markdownBulletList(values: List<String>): String =
        if (values.isEmpty()) {
            "- none"
        } else {
            values.joinToString(separator = System.lineSeparator()) { value -> "- `$value`" }
        }

    private fun loadPublicDistributionContract(projectRootDirectory: Path): PublicDistributionContract {
        val properties = Properties()
        Files.newInputStream(projectRootDirectory.resolve(PUBLIC_DISTRIBUTION_CONTRACT_PATH)).use { stream ->
            properties.load(stream)
        }
        return PublicDistributionContract(
            supportedPublicCliBundleTargets =
                parseList(properties.getProperty(SUPPORTED_BUNDLE_TARGETS_KEY)),
            unsupportedOperatingSystems =
                parseList(properties.getProperty(UNSUPPORTED_OPERATING_SYSTEMS_KEY)),
        )
    }

    private fun parseList(rawValue: String?): List<String> =
        rawValue
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.distinct()
            ?: emptyList()
}

private data class PublicDistributionContract(
    val supportedPublicCliBundleTargets: List<String>,
    val unsupportedOperatingSystems: List<String>,
)
