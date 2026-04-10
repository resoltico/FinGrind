package dev.erst.fingrind.buildlogic

import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.Project
import org.gradle.process.JavaForkOptions

internal const val NATIVE_ACCESS_ARGUMENT = "--enable-native-access=ALL-UNNAMED"

internal fun JavaForkOptions.enableNativeAccess() {
    jvmArgs(NATIVE_ACCESS_ARGUMENT)
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
