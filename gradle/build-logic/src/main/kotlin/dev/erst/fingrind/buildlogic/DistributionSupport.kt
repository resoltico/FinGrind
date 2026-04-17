package dev.erst.fingrind.buildlogic

object DistributionSupport {
    val PUBLIC_CLI_BUNDLE_TARGETS =
        listOf("macos-aarch64", "macos-x86_64", "linux-x86_64", "linux-aarch64", "windows-x86_64")

    val UNSUPPORTED_PUBLIC_CLI_OPERATING_SYSTEMS = emptyList<String>()

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
}
