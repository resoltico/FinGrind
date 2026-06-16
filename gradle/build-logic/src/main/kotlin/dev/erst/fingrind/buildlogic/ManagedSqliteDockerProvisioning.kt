package dev.erst.fingrind.buildlogic

import java.nio.file.Path
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.kotlin.dsl.register

internal fun registerDockerManagedSqliteTarget(
    project: Project,
    hostProvisioning: ManagedSqliteProvisioning,
    repositoryRootDirectory: Path,
    sqliteSourceDirectory: Directory,
    sqliteVersionValue: String,
    sqlite3mcVersionValue: String,
    sourcePackageId: String,
    dockerBundleTarget: BundleTargetContract =
        DistributionBundleTargetReader.dockerBundleTarget(repositoryRootDirectory),
): ManagedSqliteProvisioning {

    val verifyManagedSqliteSource =
        registerManagedSqliteSourceVerification(
            project = project,
            repositoryRootDirectory = repositoryRootDirectory,
            sqliteSourceDirectory = sqliteSourceDirectory,
            sqlite3mcVersionValue = sqlite3mcVersionValue,
            sourcePackageId = sourcePackageId,
            taskName = "verifyDockerManagedSqliteSource",
        )
    val provisioningPathPrefix = "managed-sqlite/docker-context/${dockerBundleTarget.classifier}"
    val libraryPath =
        project.layout.buildDirectory.file(
            "$provisioningPathPrefix/${dockerBundleTarget.sqliteLibraryFileName}",
        )
    val checksumPath =
        project.layout.buildDirectory.file(
            "$provisioningPathPrefix/${dockerBundleTarget.sqliteLibraryFileName}.sha256",
        )
    val toolchainFingerprintPath =
        project.layout.buildDirectory.file("$provisioningPathPrefix/toolchain-fingerprint.json")
    val buildContractPath =
        project.layout.buildDirectory.file("$provisioningPathPrefix/build-contract.json")
    val sqliteSourceFile = sqliteSourceDirectory.file("sqlite3mc_amalgamation.c")
    val headerFile = sqliteSourceDirectory.file("sqlite3mc_amalgamation.h")
    val extensionHeaderFile = sqliteSourceDirectory.file("sqlite3ext.h")

    val prepareDockerManagedSqlite =
        project.tasks.register<PrepareDockerManagedSqliteTask>("prepareDockerManagedSqlite") {
            group = "build setup"
            description =
                "Builds the managed SQLite $sqliteVersionValue / SQLite3 Multiple Ciphers $sqlite3mcVersionValue shared library for the Linux Docker target."
            dependsOn(verifyManagedSqliteSource)
            sourceFile.set(sqliteSourceFile.asFile)
            supportFiles.from(headerFile.asFile, extensionHeaderFile.asFile)
            architectureId.set(dockerBundleTarget.architectureId)
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
            builderImage.set(DockerManagedSqliteBuildEnvironment.builderImage)
            buildBasePackage.set(DockerManagedSqliteBuildEnvironment.buildBasePackage)
            pythonPackage.set(DockerManagedSqliteBuildEnvironment.pythonPackage)
            toolchainFingerprintFile.set(toolchainFingerprintPath)
            buildContractFile.set(buildContractPath)
            outputFile.set(libraryPath)
            checksumFile.set(checksumPath)
        }

    return ManagedSqliteProvisioning(
        classifier = dockerBundleTarget.classifier,
        libraryFileName = dockerBundleTarget.sqliteLibraryFileName,
        libraryPath = libraryPath,
        checksumPath = checksumPath,
        toolchainFingerprintPath = toolchainFingerprintPath,
        buildContractPath = buildContractPath,
        prepareTask = prepareDockerManagedSqlite,
    )
}
