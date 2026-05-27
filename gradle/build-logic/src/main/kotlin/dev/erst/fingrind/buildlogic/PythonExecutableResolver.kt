package dev.erst.fingrind.buildlogic

internal fun selectExactPythonExecutable(
    requiredPythonVersion: String,
    osName: String,
    probeVersion: (String) -> Pair<Int, Int>?,
    findUvManagedPythonExecutable: (String) -> String?,
): String? {
    val requiredMajorMinor = parseRequiredPythonMajorMinor(requiredPythonVersion)
    return exactPythonCommandCandidates(requiredMajorMinor, osName)
        .firstOrNull { candidate -> probeVersion(candidate) == requiredMajorMinor }
        ?: findUvManagedPythonExecutable(requiredPythonVersion)?.takeIf {
            probeVersion(it) == requiredMajorMinor
        }
}

internal fun exactPythonCommandCandidates(
    requiredMajorMinor: Pair<Int, Int>,
    osName: String,
): List<String> {
    val exactVersionCommand = "python${requiredMajorMinor.first}.${requiredMajorMinor.second}"
    return if (osName.lowercase().contains("windows")) {
        listOf("python", exactVersionCommand)
    } else {
        listOf(exactVersionCommand, "python${requiredMajorMinor.first}", "python3", "python")
    }
}

internal fun pythonVersionMatchesRequirement(
    detectedVersion: Pair<Int, Int>,
    requiredVersion: Pair<Int, Int>,
): Boolean = detectedVersion == requiredVersion
