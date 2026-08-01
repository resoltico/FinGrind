package dev.erst.fingrind.buildlogic

/** Validates target-supplied values before they become portable bundle archive paths. */
internal object BundleStagingContractValidation {
    fun requireSupportedArchiveFormat(value: String): String {
        val normalizedValue = requireNonBlank(value, "bundle archive format")
        require(normalizedValue == "zip" || normalizedValue == "tar.gz") {
            "Unsupported FinGrind bundle archive format: $normalizedValue."
        }
        return normalizedValue
    }

    fun runtimeJavaExecutableName(operatingSystemId: String): String =
        when (requireNonBlank(operatingSystemId, "bundle operating-system identifier")) {
            "windows" -> "java.exe"
            "linux", "macos" -> "java"
            else ->
                throw IllegalArgumentException(
                    "Unsupported FinGrind bundle operating-system identifier: $operatingSystemId.",
                )
        }

    fun requireNonBlank(value: String, label: String): String =
        value.also { checkedValue ->
            require(checkedValue.isNotBlank()) { "$label must not be blank." }
        }
}
