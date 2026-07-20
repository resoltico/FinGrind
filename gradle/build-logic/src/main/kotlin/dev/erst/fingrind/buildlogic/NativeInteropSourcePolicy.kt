package dev.erst.fingrind.buildlogic

import java.io.File

private val attestationNativeInteropSeamSourceSuffixes =
    setOf(
        "/core/src/main/java/dev/erst/fingrind/core/attestation/AttestationDirectoryFfmTransport.java",
        "/core/src/test/java/dev/erst/fingrind/core/attestation/AttestationDirectoryDurabilityTest.java",
    )
private val foreignMemoryImportPattern = Regex("""^import\s+java\.lang\.foreign\.[\w.*]+;$""")
private val fullyQualifiedForeignMemoryPattern = Regex("""\bjava\.lang\.foreign\.[A-Z]\w*""")
private val systemLoadPattern = Regex("""\bSystem\.load(?:Library)?\s*\(""")
private val runtimeLoadPattern = Regex("""\bRuntime\.getRuntime\(\)\.load(?:Library)?\s*\(""")

internal fun File.isAttestationNativeInteropSeam(): Boolean =
    attestationNativeInteropSeamSourceSuffixes.any(invariantSeparatorsPath()::endsWith)

internal fun nativeInteropPolicyViolations(
    file: File,
    line: String,
    lineNumber: Int,
    projectDirectory: File,
    sqliteOwnedProject: Boolean,
): List<String> {
    if (sqliteOwnedProject || file.isAttestationNativeInteropSeam()) {
        return emptyList()
    }
    val violationPrefix = "${file.displayPath(projectDirectory)}:$lineNumber:"
    return buildList {
        if (
            foreignMemoryImportPattern.matches(line.trim()) ||
                fullyQualifiedForeignMemoryPattern.containsMatchIn(line)
        ) {
            add(
                "$violationPrefix Java FFM usage is owned only by the SQLite bridge module and the attestation key directory-durability seam.",
            )
        }
        if (systemLoadPattern.containsMatchIn(line) || runtimeLoadPattern.containsMatchIn(line)) {
            add(
                "$violationPrefix Native library loading is owned only by the SQLite bridge module and the attestation key directory-durability seam.",
            )
        }
    }
}
