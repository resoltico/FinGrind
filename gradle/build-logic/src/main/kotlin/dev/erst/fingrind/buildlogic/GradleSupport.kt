package dev.erst.fingrind.buildlogic

import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.FileCollection
import org.gradle.api.Project
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.JavaForkOptions
import java.io.File
import java.io.Serializable

internal const val CLI_NATIVE_ACCESS_MODULE = "dev.erst.fingrind.cli"
internal const val SQLITE_NATIVE_ACCESS_MODULE = "dev.erst.fingrind.sqlite"
internal const val CORE_NATIVE_ACCESS_MODULE = "dev.erst.fingrind.core"
internal const val CLI_AND_CORE_NATIVE_ACCESS_MODULES =
    "$CLI_NATIVE_ACCESS_MODULE,$CORE_NATIVE_ACCESS_MODULE"
internal const val UNNAMED_NATIVE_ACCESS_ARGUMENT = "--enable-native-access=ALL-UNNAMED"
internal const val SUN_MISC_UNSAFE_MEMORY_ACCESS_ARGUMENT = "--sun-misc-unsafe-memory-access=allow"
internal const val DISABLE_CLASS_DATA_SHARING_ARGUMENT = "-Xshare:off"

internal fun JavaForkOptions.enableSqliteNamedNativeAccess() {
    jvmArgs("--enable-native-access=$SQLITE_NATIVE_ACCESS_MODULE")
}

internal fun JavaForkOptions.enableCoreNamedNativeAccess() {
    jvmArgs("--enable-native-access=$CORE_NATIVE_ACCESS_MODULE")
}

internal fun JavaForkOptions.enableCliAndCoreNamedNativeAccess() {
    jvmArgs("--enable-native-access=$CLI_AND_CORE_NATIVE_ACCESS_MODULES")
}

internal fun JavaForkOptions.enableJazzerNativeAccess() {
    enableUnnamedNativeAccess()
    enableCoreNamedNativeAccess()
    enableSqliteNamedNativeAccess()
}

internal fun JavaForkOptions.addSqliteNamedModule() {
    jvmArgs("--add-modules=$SQLITE_NATIVE_ACCESS_MODULE")
}

private class ModulePathArgumentProvider(
    @get:Classpath val modulePath: FileCollection,
) : CommandLineArgumentProvider, Serializable {
    override fun asArguments(): Iterable<String> =
        listOf(
            "--module-path",
            modulePath.files.joinToString(File.pathSeparator) { file -> file.absolutePath },
        )
}

private class PatchModuleArgumentProvider(
    @get:Input val moduleName: String,
    @get:Classpath val patchPath: FileCollection,
) : CommandLineArgumentProvider, Serializable {
    override fun asArguments(): Iterable<String> =
        listOf(
            "--patch-module",
            "$moduleName=${patchPath.files.joinToString(File.pathSeparator) { file -> file.absolutePath }}",
        )
}

fun JavaForkOptions.useModulePath(modulePath: FileCollection) {
    jvmArgumentProviders.add(ModulePathArgumentProvider(modulePath))
}

fun JavaForkOptions.patchModule(moduleName: String, patchPath: FileCollection) {
    jvmArgumentProviders.add(PatchModuleArgumentProvider(moduleName, patchPath))
}

fun JavaForkOptions.addReads(moduleName: String, targetModule: String) {
    jvmArgs("--add-reads=$moduleName=$targetModule")
}

fun JavaForkOptions.addOpens(moduleName: String, packageName: String, targetModule: String) {
    jvmArgs("--add-opens=$moduleName/$packageName=$targetModule")
}

internal fun JavaForkOptions.enableUnnamedNativeAccess() {
    jvmArgs(UNNAMED_NATIVE_ACCESS_ARGUMENT)
}

internal fun JavaForkOptions.allowSunMiscUnsafeMemoryAccess() {
    jvmArgs(SUN_MISC_UNSAFE_MEMORY_ACCESS_ARGUMENT)
}

internal fun JavaForkOptions.disableClassDataSharing() {
    jvmArgs(DISABLE_CLASS_DATA_SHARING_ARGUMENT)
}

internal fun VersionCatalog.library(name: String): Any =
    findLibrary(name).orElseThrow { IllegalArgumentException("Missing version-catalog library: $name") }.get()

internal fun VersionCatalog.version(name: String): String =
    findVersion(name)
        .orElseThrow { IllegalArgumentException("Missing version-catalog version: $name") }
        .requiredVersion

internal fun Project.versionCatalog(name: String = "libs"): VersionCatalog =
    extensions
        .getByType(VersionCatalogsExtension::class.java)
        .named(name)

internal fun String.toTaskSuffix(): String =
    split('-').joinToString(separator = "") { segment ->
        segment.replaceFirstChar { character -> character.titlecase() }
    }
