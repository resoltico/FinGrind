package dev.erst.fingrind.buildlogic

import org.gradle.api.artifacts.VersionCatalog
import org.gradle.process.JavaForkOptions

internal const val NATIVE_ACCESS_ARGUMENT = "--enable-native-access=ALL-UNNAMED"

internal fun JavaForkOptions.enableNativeAccess() {
    jvmArgs(NATIVE_ACCESS_ARGUMENT)
}

internal fun VersionCatalog.library(name: String): Any =
    findLibrary(name).orElseThrow { IllegalArgumentException("Missing version-catalog library: $name") }.get()

internal fun String.toTaskSuffix(): String =
    split('-').joinToString(separator = "") { segment ->
        segment.replaceFirstChar { character -> character.titlecase() }
    }
