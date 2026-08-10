package dev.erst.fingrind.buildlogic

import java.io.File

private const val PRIVATE_OUTPUT_DIRECTORY_FFM_TRANSPORT_SOURCE =
    "/core/src/main/java/dev/erst/fingrind/core/PrivateOutputDirectoryFfmTransport.java"
private const val PRIVATE_OUTPUT_DIRECTORY_PLATFORM_SPEC_SOURCE =
    "/core/src/main/java/dev/erst/fingrind/core/PrivateOutputDirectoryPlatformSpec.java"
private const val PRIVATE_OUTPUT_DIRECTORY_DURABILITY_TEST_SOURCE =
    "/core/src/test/java/dev/erst/fingrind/core/PrivateOutputDirectoryDurabilityTest.java"
private val foreignMemoryImportPattern = Regex("""^import\s+java\.lang\.foreign\.[\w.*]+;$""")
private val fullyQualifiedForeignMemoryPattern = Regex("""\bjava\.lang\.foreign\.[A-Z]\w*""")
private val systemLoadPattern = Regex("""\bSystem\.load(?:Library)?\s*\(""")
private val runtimeLoadPattern = Regex("""\bRuntime\.getRuntime\(\)\.load(?:Library)?\s*\(""")
private const val FOREIGN_MEMORY_OWNERSHIP_DESCRIPTION =
    "Java FFM usage is owned only by the SQLite bridge module, the Windows protected-output seam, " +
    "and the private-output directory-durability seam."
private const val NATIVE_LIBRARY_LOADING_OWNERSHIP_DESCRIPTION =
    "Native library loading is owned only by the SQLite bridge module."

internal fun File.isForeignMemorySeam(): Boolean {
    val sourcePath = invariantSeparatorsPath()
    return WindowsPrivateOutputFileNativeInteropSources.isNativeInteropSource(sourcePath) ||
        sourcePath.endsWith(PRIVATE_OUTPUT_DIRECTORY_FFM_TRANSPORT_SOURCE) ||
        sourcePath.endsWith(PRIVATE_OUTPUT_DIRECTORY_PLATFORM_SPEC_SOURCE) ||
        sourcePath.endsWith(PRIVATE_OUTPUT_DIRECTORY_DURABILITY_TEST_SOURCE)
}

/** The only invocation boundaries where MethodHandle's checked Throwable contract is translated. */
internal fun File.isThrowableInvocationSeam(): Boolean {
    val sourcePath = invariantSeparatorsPath()
    return WindowsPrivateOutputFileNativeInteropSources.isThrowableInvocationSource(sourcePath) ||
        sourcePath.endsWith(PRIVATE_OUTPUT_DIRECTORY_FFM_TRANSPORT_SOURCE)
}

internal fun nativeInteropPolicyViolations(
    file: File,
    line: String,
    lineNumber: Int,
    projectDirectory: File,
    sqliteOwnedProject: Boolean,
): List<String> {
    val foreignMemoryOwned = sqliteOwnedProject || file.isForeignMemorySeam()
    val foreignMemoryUsage =
        foreignMemoryImportPattern.matches(line.trim()) ||
            fullyQualifiedForeignMemoryPattern.containsMatchIn(line)
    val nativeLibraryLoading =
        systemLoadPattern.containsMatchIn(line) || runtimeLoadPattern.containsMatchIn(line)
    val violationPrefix = "${file.displayPath(projectDirectory)}:$lineNumber:"
    return buildList {
        if (!foreignMemoryOwned && foreignMemoryUsage) {
            add(
                "$violationPrefix $FOREIGN_MEMORY_OWNERSHIP_DESCRIPTION",
            )
        }
        if (!sqliteOwnedProject && nativeLibraryLoading) {
            add(
                "$violationPrefix $NATIVE_LIBRARY_LOADING_OWNERSHIP_DESCRIPTION",
            )
        }
    }
}
