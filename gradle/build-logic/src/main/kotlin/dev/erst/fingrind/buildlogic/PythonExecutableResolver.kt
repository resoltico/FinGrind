package dev.erst.fingrind.buildlogic

import java.io.File

internal fun selectExactPythonExecutable(
    requiredPythonVersion: String,
    osName: String,
    probeVersion: (String) -> Pair<Int, Int>?,
    findUvManagedPythonExecutable: (String) -> String?,
    resolveExecutablePath: (String) -> String? = { null },
): String? {
    val requiredMajorMinor = parseRequiredPythonMajorMinor(requiredPythonVersion)
    return exactPythonCommandCandidates(requiredMajorMinor, osName)
        .firstNotNullOfOrNull { candidate ->
            if (probeVersion(candidate) == requiredMajorMinor) {
                resolveExecutablePath(candidate) ?: candidate
            } else {
                null
            }
        }
        ?: findUvManagedPythonExecutable(requiredPythonVersion)?.takeIf {
            probeVersion(it) == requiredMajorMinor
        }?.let { resolveExecutablePath(it) ?: it }
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

internal fun resolveExecutableOnPath(
    command: String,
    osName: String,
    pathEntries: List<String>,
): String? {
    val commandFile = File(command)
    if (commandFile.parent != null) {
        return commandFile.takeIf(File::isFile)?.absolutePath
    }
    val candidateNames =
        if (osName.lowercase().contains("windows")) {
            listOf(command, "$command.exe", "$command.cmd", "$command.bat")
        } else {
            listOf(command)
        }
    return pathEntries
        .asSequence()
        .filter(String::isNotBlank)
        .flatMap { directory ->
            candidateNames.asSequence().map { candidateName -> File(directory, candidateName) }
        }
        .firstOrNull(File::isFile)
        ?.absolutePath
}
