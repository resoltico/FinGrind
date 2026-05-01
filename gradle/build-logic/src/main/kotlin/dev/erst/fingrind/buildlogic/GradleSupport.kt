package dev.erst.fingrind.buildlogic

import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.Project
import org.gradle.process.JavaForkOptions

internal const val NATIVE_ACCESS_ARGUMENT = "--enable-native-access=ALL-UNNAMED"
internal const val SUN_MISC_UNSAFE_MEMORY_ACCESS_ARGUMENT = "--sun-misc-unsafe-memory-access=allow"
internal const val DISABLE_CLASS_DATA_SHARING_ARGUMENT = "-Xshare:off"

internal fun JavaForkOptions.enableNativeAccess() {
    jvmArgs(NATIVE_ACCESS_ARGUMENT)
}

internal fun JavaForkOptions.allowSunMiscUnsafeMemoryAccess() {
    jvmArgs(SUN_MISC_UNSAFE_MEMORY_ACCESS_ARGUMENT)
}

internal fun JavaForkOptions.disableClassDataSharing() {
    jvmArgs(DISABLE_CLASS_DATA_SHARING_ARGUMENT)
}

internal fun VersionCatalog.library(name: String): Any =
    findLibrary(name).orElseThrow { IllegalArgumentException("Missing version-catalog library: $name") }.get()

internal fun Project.versionCatalog(name: String = "libs"): VersionCatalog =
    extensions
        .getByType(VersionCatalogsExtension::class.java)
        .named(name)

internal fun String.toTaskSuffix(): String =
    split('-').joinToString(separator = "") { segment ->
        segment.replaceFirstChar { character -> character.titlecase() }
    }
