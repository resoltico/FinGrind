package dev.erst.fingrind.buildlogic

import java.io.File
import java.nio.file.Path
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
    val checksumPath: Provider<RegularFile>,
    val trustedChecksumPath: Provider<RegularFile>,
    val toolchainFingerprintPath: Provider<RegularFile>,
    val prepareTask: TaskProvider<PrepareManagedSqliteTask>,
)

internal object ManagedSqliteProvisioningLogic {
    private const val SQLITE_LIBRARY_ENVIRONMENT = "FINGRIND_SQLITE_LIBRARY"

    fun register(
        project: Project,
        repositoryRootDirectory: Path,
        sqliteSourceDirectory: Directory,
        sqliteVersionValue: String,
        sqlite3mcVersionValue: String,
        sourcePackageId: String,
    ): ManagedSqliteProvisioning {
        val hostBundleTarget =
            try {
                DistributionContractReader.hostBundleTarget(repositoryRootDirectory)
            } catch (_: IllegalStateException) {
                throw GradleException(
                    "FinGrind's managed SQLite build currently supports only declared bundle-layout targets. Detected host: ${System.getProperty("os.name")} / ${System.getProperty("os.arch")}",
                )
            }
        val managedSqliteOperatingSystemId = hostBundleTarget.operatingSystemId
        val classifier = hostBundleTarget.classifier
        val libraryFileName = hostBundleTarget.sqliteLibraryFileName
        val sqliteSourceFile = sqliteSourceDirectory.file("sqlite3mc_amalgamation.c")
        val headerFile = sqliteSourceDirectory.file("sqlite3mc_amalgamation.h")
        val sqliteHeaderFile = sqliteSourceDirectory.file("sqlite3.h")
        val extensionHeaderFile = sqliteSourceDirectory.file("sqlite3ext.h")
        val defaultCompiler =
            if (managedSqliteOperatingSystemId == "windows") {
                "cl"
            } else {
                "cc"
            }
        val sqliteCompiler =
            project.providers.environmentVariable("CC").orNull
                ?.takeIf { candidate ->
                    (!candidate.contains("/") && !candidate.contains("\\")) || File(candidate).isFile
                }
                ?: defaultCompiler
        val libraryPath =
            project.layout.buildDirectory.file("managed-sqlite/$classifier/$libraryFileName")
        val checksumPath =
            project.layout.buildDirectory.file("managed-sqlite/$classifier/$libraryFileName.sha256")
        val trustedChecksumPath =
            project.layout.buildDirectory.file(
                "managed-sqlite/$classifier/$libraryFileName.trusted.sha256",
            )
        val toolchainFingerprintPath =
            project.layout.buildDirectory.file("managed-sqlite/$classifier/toolchain-fingerprint.json")

        val verifyManagedSqliteSource =
            project.tasks.register<VerifyManagedSqliteSourceTask>("verifyManagedSqliteSource") {
                group = "build setup"
                description =
                    "Verifies the vendored SQLite3 Multiple Ciphers $sqlite3mcVersionValue release payload matches the pinned upstream manifest."
                sourceDirectory.set(sqliteSourceDirectory)
                expectedSourcePackageId.set(sourcePackageId)
                expectedFileDigests.set(
                    DistributionContractReader.vendoredSqliteReleaseFiles(repositoryRootDirectory),
                )
            }

        val probeManagedSqliteToolchain =
            project.tasks.register<ProbeManagedSqliteToolchainTask>("probeManagedSqliteToolchain") {
                group = "build setup"
                description =
                    "Captures the compiler, linker, target, and SDK identity for the managed SQLite native build."
                compiler.set(sqliteCompiler)
                operatingSystemId.set(managedSqliteOperatingSystemId)
                hostArchitecture.set(DistributionContractReader.architectureId())
                outputFile.set(toolchainFingerprintPath)
            }

        val prepareManagedSqlite =
            project.tasks.register<PrepareManagedSqliteTask>("prepareManagedSqlite") {
                group = "build setup"
                description =
                    "Builds the managed SQLite $sqliteVersionValue / SQLite3 Multiple Ciphers $sqlite3mcVersionValue shared library for the current host."
                dependsOn(verifyManagedSqliteSource)
                dependsOn(probeManagedSqliteToolchain)
                sourceFile.set(sqliteSourceFile.asFile)
                supportFiles.from(headerFile.asFile, sqliteHeaderFile.asFile, extensionHeaderFile.asFile)
                compiler.set(sqliteCompiler)
                operatingSystemId.set(managedSqliteOperatingSystemId)
                sqliteVersion.set(sqliteVersionValue)
                requiredCompileOptions.set(
                    DistributionContractReader.requiredSqliteCompileOptions(repositoryRootDirectory),
                )
                requiresSecureMemorySupport.set(
                    DistributionContractReader.requiresSecureMemorySupport(repositoryRootDirectory),
                )
                unixCompilerHardeningFlags.set(
                    DistributionContractReader.unixCompilerHardeningFlags(repositoryRootDirectory),
                )
                linuxLinkerHardeningFlags.set(
                    DistributionContractReader.linuxLinkerHardeningFlags(repositoryRootDirectory),
                )
                macosLinkerHardeningFlags.set(
                    DistributionContractReader.macosLinkerHardeningFlags(repositoryRootDirectory),
                )
                windowsCompilerHardeningFlags.set(
                    DistributionContractReader.windowsCompilerHardeningFlags(repositoryRootDirectory),
                )
                windowsLinkerHardeningFlags.set(
                    DistributionContractReader.windowsLinkerHardeningFlags(repositoryRootDirectory),
                )
                toolchainFingerprintFile.set(toolchainFingerprintPath)
                outputFile.set(libraryPath)
                checksumFile.set(checksumPath)
                trustedChecksumFile.set(trustedChecksumPath)
            }

        return ManagedSqliteProvisioning(
            classifier = classifier,
            libraryFileName = libraryFileName,
            libraryPath = libraryPath,
            checksumPath = checksumPath,
            trustedChecksumPath = trustedChecksumPath,
            toolchainFingerprintPath = toolchainFingerprintPath,
            prepareTask = prepareManagedSqlite,
        )
    }

    fun configureConsumers(project: Project, provisioning: ManagedSqliteProvisioning) {
        val libraryAbsolutePath = provisioning.libraryPath.get().asFile.absolutePath
        val operatorTrustSystemProperty =
            DistributionContractReader.sqliteOperatorTrustSystemProperty(project.rootProject.projectDir.toPath())
        project.tasks.withType<Test>().configureEach {
            dependsOn(provisioning.prepareTask)
            environment(SQLITE_LIBRARY_ENVIRONMENT, libraryAbsolutePath)
            systemProperty(operatorTrustSystemProperty, "true")
            enableNativeAccess()
        }
        project.tasks.withType<JavaExec>().configureEach {
            dependsOn(provisioning.prepareTask)
            environment(SQLITE_LIBRARY_ENVIRONMENT, libraryAbsolutePath)
            systemProperty(operatorTrustSystemProperty, "true")
            enableNativeAccess()
        }
    }

}
