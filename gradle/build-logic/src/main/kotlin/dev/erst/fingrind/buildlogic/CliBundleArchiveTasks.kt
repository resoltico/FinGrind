package dev.erst.fingrind.buildlogic

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.register

internal data class CliBundleArchiveRegistration(
    val archiveTask: TaskProvider<out AbstractArchiveTask>,
    val checksumTask: TaskProvider<WriteSha256FileTask>,
    val manifestTask: TaskProvider<WriteBundleArchiveManifestTask>,
)

internal fun Project.registerCliBundleArchiveTasks(
    bundleArchiveFormat: String,
    bundleArchiveInputTask: TaskProvider<out Task>,
    bundleArchiveMemberValidationTask: TaskProvider<out Task>,
    distributionDirectory: Provider<Directory>,
    bundleArchiveFileName: Provider<String>,
    bundleWorkspaceDirectory: Provider<Directory>,
    bundleName: Provider<String>,
    bundleSha256File: Provider<RegularFile>,
    bundleArchiveManifestFile: Provider<RegularFile>,
): CliBundleArchiveRegistration {
    val bundleArchiveTask: TaskProvider<out AbstractArchiveTask> =
        when (bundleArchiveFormat) {
            "zip" -> tasks.register<Zip>("bundleCliZip") {
                configureCliBundleArchive(
                    bundleArchiveInputTask = bundleArchiveInputTask,
                    bundleArchiveMemberValidationTask = bundleArchiveMemberValidationTask,
                    distributionDirectory = distributionDirectory,
                    bundleArchiveFileName = bundleArchiveFileName,
                    bundleWorkspaceDirectory = bundleWorkspaceDirectory,
                    bundleName = bundleName,
                )
            }
            "tar.gz" -> tasks.register<Tar>("bundleCliTarGz") {
                compression = Compression.GZIP
                configureCliBundleArchive(
                    bundleArchiveInputTask = bundleArchiveInputTask,
                    bundleArchiveMemberValidationTask = bundleArchiveMemberValidationTask,
                    distributionDirectory = distributionDirectory,
                    bundleArchiveFileName = bundleArchiveFileName,
                    bundleWorkspaceDirectory = bundleWorkspaceDirectory,
                    bundleName = bundleName,
                )
            }
            else ->
                error("Unsupported FinGrind bundle archive format: $bundleArchiveFormat.")
        }

    val bundleCliSha256 =
        tasks.register<WriteSha256FileTask>("bundleCliSha256") {
            group = "distribution"
            description = "Writes the SHA-256 checksum file for the FinGrind CLI bundle archive."
            dependsOn(bundleArchiveTask)
            inputFile.set(bundleArchiveTask.flatMap { it.archiveFile })
            outputFile.set(bundleSha256File)
        }

    val bundleCliArchive =
        tasks.register<WriteBundleArchiveManifestTask>("bundleCliArchive") {
            group = "distribution"
            description =
                "Builds the self-contained FinGrind CLI bundle archive together with its SHA-256 checksum."
            dependsOn(bundleArchiveTask)
            dependsOn(bundleCliSha256)
            archiveFile.set(bundleArchiveTask.flatMap { it.archiveFile })
            checksumFile.set(bundleCliSha256.flatMap { it.outputFile })
            outputFile.set(bundleArchiveManifestFile)
        }

    return CliBundleArchiveRegistration(
        archiveTask = bundleArchiveTask,
        checksumTask = bundleCliSha256,
        manifestTask = bundleCliArchive,
    )
}

private fun AbstractArchiveTask.configureCliBundleArchive(
    bundleArchiveInputTask: TaskProvider<out Task>,
    bundleArchiveMemberValidationTask: TaskProvider<out Task>,
    distributionDirectory: Provider<Directory>,
    bundleArchiveFileName: Provider<String>,
    bundleWorkspaceDirectory: Provider<Directory>,
    bundleName: Provider<String>,
) {
    group = "distribution"
    description = "Builds the compressed self-contained FinGrind CLI bundle archive."
    dependsOn(bundleArchiveInputTask)
    dependsOn(bundleArchiveMemberValidationTask)
    destinationDirectory.set(distributionDirectory)
    archiveFileName.set(bundleArchiveFileName)
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = true
    dirPermissions {
        unix(CliBundleArchivePermissions.DIRECTORY_UNIX_MODE)
    }
    filePermissions {
        unix(CliBundleArchivePermissions.REGULAR_FILE_UNIX_MODE)
    }
    val archiveRootName = bundleName.get()
    from(bundleWorkspaceDirectory) {
        include(archiveRootName)
        include("$archiveRootName/**")
        eachFile {
            val sourceFileIsExecutable = file.canExecute()
            permissions {
                unix(CliBundleArchivePermissions.fileUnixMode(sourceFileIsExecutable))
            }
        }
    }
}
