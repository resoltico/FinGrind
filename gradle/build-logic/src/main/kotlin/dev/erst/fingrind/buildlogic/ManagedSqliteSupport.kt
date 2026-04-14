package dev.erst.fingrind.buildlogic

import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

internal data class ManagedSqliteProvisioning(
    val classifier: String,
    val libraryFileName: String,
    val libraryPath: Provider<RegularFile>,
    val prepareTask: TaskProvider<PrepareManagedSqliteTask>,
)

internal object ManagedSqliteSupport {
    private const val SQLITE_LIBRARY_ENVIRONMENT = "FINGRIND_SQLITE_LIBRARY"

    fun register(
        project: Project,
        sourceDirectory: Directory,
        sqliteVersionValue: String,
        sqlite3mcVersionValue: String,
        sourceSha3: String,
    ): ManagedSqliteProvisioning {
        val managedSqliteOperatingSystemId = operatingSystemId()
        val classifier = "$managedSqliteOperatingSystemId-${architectureId()}"
        val libraryFileName = libraryFileName(managedSqliteOperatingSystemId)
        val sqliteSourceFile = sourceDirectory.file("sqlite3mc_amalgamation.c")
        val headerFile = sourceDirectory.file("sqlite3mc_amalgamation.h")
        val sqliteHeaderFile = sourceDirectory.file("sqlite3.h")
        val extensionHeaderFile = sourceDirectory.file("sqlite3ext.h")
        val sqliteCompiler =
            project.providers.environmentVariable("CC").orNull
                ?.takeIf { candidate -> !candidate.contains("/") || File(candidate).isFile }
                ?: "cc"
        val libraryPath =
            project.layout.buildDirectory.file("managed-sqlite/$classifier/$libraryFileName")

        val verifyManagedSqliteSource =
            project.tasks.register<VerifyManagedSqliteSourceTask>("verifyManagedSqliteSource") {
                group = "build setup"
                description =
                    "Verifies the vendored SQLite3 Multiple Ciphers $sqlite3mcVersionValue amalgamation matches the pinned upstream source hash."
                sourceFile.set(sqliteSourceFile.asFile)
                expectedSha3.set(sourceSha3)
            }

        val prepareManagedSqlite =
            project.tasks.register<PrepareManagedSqliteTask>("prepareManagedSqlite") {
                group = "build setup"
                description =
                    "Builds the managed SQLite $sqliteVersionValue / SQLite3 Multiple Ciphers $sqlite3mcVersionValue shared library for the current host."
                dependsOn(verifyManagedSqliteSource)
                sourceFile.set(sqliteSourceFile.asFile)
                supportFiles.from(headerFile.asFile, sqliteHeaderFile.asFile, extensionHeaderFile.asFile)
                compiler.set(sqliteCompiler)
                operatingSystemId.set(managedSqliteOperatingSystemId)
                sqliteVersion.set(sqliteVersionValue)
                outputFile.set(libraryPath)
            }

        return ManagedSqliteProvisioning(
            classifier = classifier,
            libraryFileName = libraryFileName,
            libraryPath = libraryPath,
            prepareTask = prepareManagedSqlite,
        )
    }

    fun configureConsumers(project: Project, provisioning: ManagedSqliteProvisioning) {
        val libraryAbsolutePath = provisioning.libraryPath.get().asFile.absolutePath
        project.tasks.withType<Test>().configureEach {
            dependsOn(provisioning.prepareTask)
            environment(SQLITE_LIBRARY_ENVIRONMENT, libraryAbsolutePath)
        }
        project.tasks.withType<JavaExec>().configureEach {
            dependsOn(provisioning.prepareTask)
            environment(SQLITE_LIBRARY_ENVIRONMENT, libraryAbsolutePath)
        }
    }

    private fun operatingSystemId(): String {
        val operatingSystem = System.getProperty("os.name", "").lowercase()
        if (operatingSystem.contains("mac")) {
            return "macos"
        }
        if (operatingSystem.contains("linux")) {
            return "linux"
        }
        throw GradleException(
            "FinGrind's managed SQLite build currently supports macOS and Linux only. Detected: ${System.getProperty("os.name")}",
        )
    }

    private fun architectureId(): String =
        System.getProperty("os.arch", "unknown").lowercase().replace(Regex("[^a-z0-9]+"), "-")

    private fun libraryFileName(operatingSystemId: String): String =
        when (operatingSystemId) {
            "macos" -> "libsqlite3.dylib"
            "linux" -> "libsqlite3.so.0"
            else -> throw GradleException("Unsupported managed SQLite operating system id: $operatingSystemId")
        }
}
