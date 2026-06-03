package dev.erst.fingrind.buildlogic

import java.io.File
import java.nio.file.Path
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register

internal fun registerLocalManagedSqliteTarget(
    project: Project,
    repositoryRootDirectory: Path,
    sqliteSourceDirectory: Directory,
    sqliteVersionValue: String,
    sqlite3mcVersionValue: String,
    sourcePackageId: String,
    bundleTarget: DistributionContractReader.BundleTargetContract,
    taskName: String,
    sourceVerificationTaskName: String,
    toolchainProbeTaskName: String,
    taskDescription: String,
    provisioningPathPrefix: String,
): ManagedSqliteProvisioning {
    val sqliteSourceFile = sqliteSourceDirectory.file("sqlite3mc_amalgamation.c")
    val headerFile = sqliteSourceDirectory.file("sqlite3mc_amalgamation.h")
    val sqliteHeaderFile = sqliteSourceDirectory.file("sqlite3.h")
    val extensionHeaderFile = sqliteSourceDirectory.file("sqlite3ext.h")
    val activeOperatingSystemId = bundleTarget.operatingSystemId
    val defaultCompiler =
        if (activeOperatingSystemId == "windows") {
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
        project.layout.buildDirectory.file("$provisioningPathPrefix/${bundleTarget.sqliteLibraryFileName}")
    val checksumPath =
        project.layout.buildDirectory.file(
            "$provisioningPathPrefix/${bundleTarget.sqliteLibraryFileName}.sha256",
        )
    val toolchainFingerprintPath =
        project.layout.buildDirectory.file("$provisioningPathPrefix/toolchain-fingerprint.json")
    val buildContractPath =
        project.layout.buildDirectory.file("$provisioningPathPrefix/build-contract.json")

    val verifyManagedSqliteSource =
        registerManagedSqliteSourceVerification(
            project = project,
            repositoryRootDirectory = repositoryRootDirectory,
            sqliteSourceDirectory = sqliteSourceDirectory,
            sqlite3mcVersionValue = sqlite3mcVersionValue,
            sourcePackageId = sourcePackageId,
            taskName = sourceVerificationTaskName,
        )

    val probeManagedSqliteToolchain =
        project.tasks.register<ProbeManagedSqliteToolchainTask>(toolchainProbeTaskName) {
            group = "build setup"
            description =
                "Captures the compiler, linker, target, and SDK identity for the managed SQLite native build."
            compiler.set(sqliteCompiler)
            operatingSystemId.set(activeOperatingSystemId)
            architectureId.set(bundleTarget.architectureId)
            outputFile.set(toolchainFingerprintPath)
        }

    val prepareManagedSqlite =
        project.tasks.register<PrepareManagedSqliteTask>(taskName) {
            group = "build setup"
            description = taskDescription
            dependsOn(verifyManagedSqliteSource)
            dependsOn(probeManagedSqliteToolchain)
            sourceFile.set(sqliteSourceFile.asFile)
            supportFiles.from(headerFile.asFile, sqliteHeaderFile.asFile, extensionHeaderFile.asFile)
            compiler.set(sqliteCompiler)
            operatingSystemId.set(activeOperatingSystemId)
            sqliteVersion.set(sqliteVersionValue)
            requiredCompileOptions.set(
                DistributionContractReader.requiredSqliteCompileOptions(repositoryRootDirectory),
            )
            forbiddenCompileOptions.set(
                DistributionContractReader.forbiddenSqliteCompileOptions(repositoryRootDirectory),
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
            buildContractFile.set(buildContractPath)
            outputFile.set(libraryPath)
            checksumFile.set(checksumPath)
        }

    return ManagedSqliteProvisioning(
        classifier = bundleTarget.classifier,
        libraryFileName = bundleTarget.sqliteLibraryFileName,
        libraryPath = libraryPath,
        checksumPath = checksumPath,
        toolchainFingerprintPath = toolchainFingerprintPath,
        buildContractPath = buildContractPath,
        prepareTask = prepareManagedSqlite,
    )
}

internal fun registerManagedSqliteSourceVerification(
    project: Project,
    repositoryRootDirectory: Path,
    sqliteSourceDirectory: Directory,
    sqlite3mcVersionValue: String,
    sourcePackageId: String,
    taskName: String,
): TaskProvider<VerifyManagedSqliteSourceTask> =
    project.tasks.register<VerifyManagedSqliteSourceTask>(taskName) {
        group = "build setup"
        description =
            "Verifies the vendored SQLite3 Multiple Ciphers $sqlite3mcVersionValue release payload matches the pinned upstream manifest."
        sourceDirectory.set(sqliteSourceDirectory)
        expectedSourcePackageId.set(sourcePackageId)
        expectedFileDigests.set(
            DistributionContractReader.vendoredSqliteReleaseFiles(repositoryRootDirectory),
        )
    }
