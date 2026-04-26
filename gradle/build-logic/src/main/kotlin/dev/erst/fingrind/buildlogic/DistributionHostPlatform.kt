package dev.erst.fingrind.buildlogic

internal object DistributionHostPlatform {
    fun operatingSystemId(osName: String): String {
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

    fun architectureId(architecture: String): String =
        when (architecture.lowercase()) {
            "arm64", "aarch64" -> "aarch64"
            "amd64", "x86_64", "x64" -> "x86_64"
            else -> architecture.lowercase().replace(Regex("[^a-z0-9]+"), "-")
        }
}
