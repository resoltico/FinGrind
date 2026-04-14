package dev.erst.fingrind.buildlogic

object DistributionSupport {
    val PUBLIC_CLI_BUNDLE_TARGETS =
        listOf("macos-aarch64", "macos-x86_64", "linux-x86_64", "linux-aarch64")

    val UNSUPPORTED_PUBLIC_CLI_OPERATING_SYSTEMS = listOf("windows")

    fun operatingSystemId(osName: String = System.getProperty("os.name", "")): String {
        val operatingSystem = osName.lowercase()
        if (operatingSystem.contains("mac")) {
            return "macos"
        }
        if (operatingSystem.contains("linux")) {
            return "linux"
        }
        throw IllegalStateException("FinGrind bundles currently support macOS and Linux only.")
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
            else -> throw IllegalStateException("Unsupported operating system id: $operatingSystemId")
        }

    fun jsonStringArray(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]") { value -> "\"$value\"" }

    fun markdownBulletList(values: List<String>): String =
        values.joinToString(separator = System.lineSeparator()) { value -> "- `$value`" }
}
