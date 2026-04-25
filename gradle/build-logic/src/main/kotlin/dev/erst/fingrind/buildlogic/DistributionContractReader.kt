package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

object DistributionContractReader {
    private val objectMapper = JsonMapper.builder().build()
    private const val PUBLIC_DISTRIBUTION_CONTRACT_PATH =
        "contract/src/main/resources/dev/erst/fingrind/contract/protocol/public-distribution-contract.json"
    private const val OPERATION_ID_CONTRACT_PATH =
        "contract/src/main/resources/dev/erst/fingrind/contract/protocol/operation-id-contract.json"
    private const val RUNTIME_SURFACE_CONTRACT_PATH =
        "contract/src/main/resources/dev/erst/fingrind/contract/protocol/runtime-surface-contract.json"
    private const val SUPPORTED_BUNDLE_TARGETS_KEY = "supportedPublicCliBundleTargets"
    private const val UNSUPPORTED_OPERATING_SYSTEMS_KEY = "unsupportedPublicCliOperatingSystems"
    private const val SOURCE_CHECKOUT_RUNTIME_DISTRIBUTION_KEY = "sourceCheckoutRuntimeDistribution"
    private const val CONTAINER_RUNTIME_DISTRIBUTION_KEY = "containerRuntimeDistribution"
    private const val BUNDLE_RUNTIME_DISTRIBUTION_KEY = "bundleRuntimeDistribution"
    private const val PUBLIC_CLI_DISTRIBUTION_KEY = "publicCliDistribution"
    private const val STORAGE_DRIVER_KEY = "storageDriver"
    private const val STORAGE_ENGINE_KEY = "storageEngine"
    private const val BOOK_PROTECTION_MODE_KEY = "bookProtectionMode"
    private const val DEFAULT_BOOK_CIPHER_KEY = "defaultBookCipher"
    private const val SQLITE_LIBRARY_MODE_KEY = "sqliteLibraryMode"
    private const val SQLITE_BUNDLE_HOME_SYSTEM_PROPERTY_KEY = "sqliteBundleHomeSystemProperty"

    fun publicCliBundleTargets(projectRootDirectory: Path): List<String> =
        listProperty(projectRootDirectory, PUBLIC_DISTRIBUTION_CONTRACT_PATH, SUPPORTED_BUNDLE_TARGETS_KEY)

    fun unsupportedPublicCliOperatingSystems(projectRootDirectory: Path): List<String> =
        listProperty(projectRootDirectory, PUBLIC_DISTRIBUTION_CONTRACT_PATH, UNSUPPORTED_OPERATING_SYSTEMS_KEY)

    fun sourceCheckoutRuntimeDistribution(projectRootDirectory: Path): String =
        requiredProperty(
            projectRootDirectory,
            RUNTIME_SURFACE_CONTRACT_PATH,
            SOURCE_CHECKOUT_RUNTIME_DISTRIBUTION_KEY,
        )

    fun containerRuntimeDistribution(projectRootDirectory: Path): String =
        requiredProperty(
            projectRootDirectory,
            RUNTIME_SURFACE_CONTRACT_PATH,
            CONTAINER_RUNTIME_DISTRIBUTION_KEY,
        )

    fun bundleRuntimeDistribution(projectRootDirectory: Path): String =
        requiredProperty(
            projectRootDirectory,
            RUNTIME_SURFACE_CONTRACT_PATH,
            BUNDLE_RUNTIME_DISTRIBUTION_KEY,
        )

    fun publicCliDistribution(projectRootDirectory: Path): String =
        requiredProperty(projectRootDirectory, RUNTIME_SURFACE_CONTRACT_PATH, PUBLIC_CLI_DISTRIBUTION_KEY)

    fun storageDriver(projectRootDirectory: Path): String =
        requiredProperty(projectRootDirectory, RUNTIME_SURFACE_CONTRACT_PATH, STORAGE_DRIVER_KEY)

    fun storageEngine(projectRootDirectory: Path): String =
        requiredProperty(projectRootDirectory, RUNTIME_SURFACE_CONTRACT_PATH, STORAGE_ENGINE_KEY)

    fun bookProtectionMode(projectRootDirectory: Path): String =
        requiredProperty(projectRootDirectory, RUNTIME_SURFACE_CONTRACT_PATH, BOOK_PROTECTION_MODE_KEY)

    fun defaultBookCipher(projectRootDirectory: Path): String =
        requiredProperty(projectRootDirectory, RUNTIME_SURFACE_CONTRACT_PATH, DEFAULT_BOOK_CIPHER_KEY)

    fun sqliteLibraryMode(projectRootDirectory: Path): String =
        requiredProperty(projectRootDirectory, RUNTIME_SURFACE_CONTRACT_PATH, SQLITE_LIBRARY_MODE_KEY)

    fun sqliteBundleHomeSystemProperty(projectRootDirectory: Path): String =
        requiredProperty(
            projectRootDirectory,
            RUNTIME_SURFACE_CONTRACT_PATH,
            SQLITE_BUNDLE_HOME_SYSTEM_PROPERTY_KEY,
        )

    fun helpOperationName(projectRootDirectory: Path): String =
        requiredProperty(projectRootDirectory, OPERATION_ID_CONTRACT_PATH, "HELP")

    fun capabilitiesOperationName(projectRootDirectory: Path): String =
        requiredProperty(projectRootDirectory, OPERATION_ID_CONTRACT_PATH, "CAPABILITIES")

    fun requestTemplateOperationName(projectRootDirectory: Path): String =
        requiredProperty(projectRootDirectory, OPERATION_ID_CONTRACT_PATH, "PRINT_REQUEST_TEMPLATE")

    fun planTemplateOperationName(projectRootDirectory: Path): String =
        requiredProperty(projectRootDirectory, OPERATION_ID_CONTRACT_PATH, "PRINT_PLAN_TEMPLATE")

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
            "windows" -> "bin/fingrind.ps1"
            else -> throw IllegalStateException("Unsupported operating system id: $operatingSystemId")
        }

    fun launcherCommandForOperatingSystemId(operatingSystemId: String): String =
        when (operatingSystemId) {
            "macos", "linux" -> "./bin/fingrind"
            "windows" -> ".\\bin\\fingrind.ps1"
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

    private fun loadJson(projectRootDirectory: Path, relativePath: String): JsonNode {
        Files.newInputStream(projectRootDirectory.resolve(relativePath)).use { stream ->
            val document = objectMapper.readTree(stream)
            if (document == null || !document.isObject) {
                throw IllegalStateException("Contract resource $relativePath must contain one top-level JSON object.")
            }
            return document
        }
    }

    private fun requiredProperty(
        projectRootDirectory: Path,
        relativePath: String,
        key: String,
    ): String {
        val valueNode = loadJson(projectRootDirectory, relativePath).path(key)
        if (!valueNode.isTextual) {
            throw IllegalStateException("Missing required contract property $key in $relativePath.")
        }
        val value = valueNode.textValue()?.trim().orEmpty()
        if (value.isEmpty()) {
            throw IllegalStateException("Missing required contract property $key in $relativePath.")
        }
        return value
    }

    private fun listProperty(
        projectRootDirectory: Path,
        relativePath: String,
        key: String,
    ): List<String> {
        val valuesNode = loadJson(projectRootDirectory, relativePath).path(key)
        if (valuesNode.isMissingNode || valuesNode.isNull) {
            return emptyList()
        }
        if (!valuesNode.isArray) {
            throw IllegalStateException("Expected JSON array contract property $key in $relativePath.")
        }
        val values = linkedSetOf<String>()
        valuesNode.forEach { node ->
            if (!node.isTextual) {
                throw IllegalStateException("Expected JSON string elements in contract property $key in $relativePath.")
            }
            val value = node.textValue()?.trim().orEmpty()
            if (value.isNotEmpty()) {
                values += value
            }
        }
        return values.toList()
    }
}
