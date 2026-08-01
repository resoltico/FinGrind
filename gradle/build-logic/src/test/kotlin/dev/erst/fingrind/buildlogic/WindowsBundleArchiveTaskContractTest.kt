package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.register
import org.gradle.testfixtures.ProjectBuilder

class WindowsBundleArchiveTaskContractTest {
    @Test
    fun publishedWindowsTargetConfiguresTheCanonicalZipPathAndFixedPermissionsWithoutCreatingIt() {
        val repositoryRoot = repositoryRoot()
        val layout =
            BundleStagingLayout.plan(
                projectRootDirectory = repositoryRoot,
                version = "1.2.3",
                classifier = "windows-x86_64",
            )
        val projectDirectory = Files.createTempDirectory("windows-bundle-archive-task")
        try {
            val project = ProjectBuilder.builder().withProjectDir(projectDirectory.toFile()).build()
            val distributionsDirectory = project.layout.buildDirectory.dir("fixture-distributions")
            val registration =
                project.registerCliBundleArchiveTasks(
                    bundleArchiveFormat = layout.archiveFormat,
                    bundleArchiveInputTask = project.tasks.register("fixtureBundleInput"),
                    bundleArchiveMemberValidationTask =
                        project.tasks.register("fixtureBundleMemberValidation"),
                    distributionDirectory = distributionsDirectory,
                    bundleArchiveFileName = project.provider { layout.archiveFileName },
                    bundleWorkspaceDirectory = project.layout.buildDirectory.dir("fixture-workspace"),
                    bundleName = project.provider { layout.bundleName },
                    bundleSha256File = project.layout.buildDirectory.file("fixture-distributions/checksum"),
                    bundleArchiveManifestFile =
                        project.layout.buildDirectory.file("fixture-distributions/manifest.json"),
                )
            val archiveTask = assertIs<Zip>(registration.archiveTask.get())
            val expectedArchive =
                distributionsDirectory.get().asFile.toPath().resolve("fingrind-1.2.3-windows-x86_64.zip")

            assertEquals("bundleCliZip", archiveTask.name)
            assertEquals("fingrind-1.2.3-windows-x86_64.zip", archiveTask.archiveFileName.get())
            assertEquals(expectedArchive.toFile(), archiveTask.archiveFile.get().asFile)
            assertEquals(
                CliBundleArchivePermissions.DIRECTORY_UNIX_MODE,
                archiveTask.dirPermissions.get().toUnixNumeric(),
            )
            assertEquals(
                CliBundleArchivePermissions.REGULAR_FILE_UNIX_MODE,
                archiveTask.filePermissions.get().toUnixNumeric(),
            )
            assertEquals(
                CliBundleArchivePermissions.EXECUTABLE_FILE_UNIX_MODE,
                CliBundleArchivePermissions.fileUnixMode(sourceFileIsExecutable = true),
            )
            assertEquals(
                CliBundleArchivePermissions.REGULAR_FILE_UNIX_MODE,
                CliBundleArchivePermissions.fileUnixMode(sourceFileIsExecutable = false),
            )
            assertTrue(archiveTask.isReproducibleFileOrder)
            assertTrue(archiveTask.isPreserveFileTimestamps)
            assertTrue(
                archiveTask.taskDependencies
                    .getDependencies(archiveTask)
                    .any { task -> task.name == "fixtureBundleMemberValidation" },
            )
            assertFalse(Files.exists(expectedArchive))
        } finally {
            DistributionContractReaderTestSupport.deleteTree(projectDirectory)
        }
    }

    @Test
    fun tarTargetsReuseTheSameArchivePolicyWithOnlyTheCompressionBoundaryChanged() {
        val repositoryRoot = repositoryRoot()
        val layout =
            BundleStagingLayout.plan(
                projectRootDirectory = repositoryRoot,
                version = "1.2.3",
                classifier = "linux-x86_64",
            )
        val projectDirectory = Files.createTempDirectory("tar-bundle-archive-task")
        try {
            val project = ProjectBuilder.builder().withProjectDir(projectDirectory.toFile()).build()
            val distributionsDirectory = project.layout.buildDirectory.dir("fixture-distributions")
            val registration =
                project.registerCliBundleArchiveTasks(
                    bundleArchiveFormat = layout.archiveFormat,
                    bundleArchiveInputTask = project.tasks.register("fixtureBundleInput"),
                    bundleArchiveMemberValidationTask =
                        project.tasks.register("fixtureBundleMemberValidation"),
                    distributionDirectory = distributionsDirectory,
                    bundleArchiveFileName = project.provider { layout.archiveFileName },
                    bundleWorkspaceDirectory = project.layout.buildDirectory.dir("fixture-workspace"),
                    bundleName = project.provider { layout.bundleName },
                    bundleSha256File = project.layout.buildDirectory.file("fixture-distributions/checksum"),
                    bundleArchiveManifestFile =
                        project.layout.buildDirectory.file("fixture-distributions/manifest.json"),
                )
            val archiveTask = assertIs<Tar>(registration.archiveTask.get())
            val expectedArchive =
                distributionsDirectory.get().asFile.toPath().resolve("fingrind-1.2.3-linux-x86_64.tar.gz")

            assertEquals("bundleCliTarGz", archiveTask.name)
            assertEquals(Compression.GZIP, archiveTask.compression)
            assertEquals(expectedArchive.toFile(), archiveTask.archiveFile.get().asFile)
            assertEquals(
                CliBundleArchivePermissions.DIRECTORY_UNIX_MODE,
                archiveTask.dirPermissions.get().toUnixNumeric(),
            )
            assertEquals(
                CliBundleArchivePermissions.REGULAR_FILE_UNIX_MODE,
                archiveTask.filePermissions.get().toUnixNumeric(),
            )
            assertTrue(archiveTask.isReproducibleFileOrder)
            assertTrue(archiveTask.isPreserveFileTimestamps)
            assertTrue(
                archiveTask.taskDependencies
                    .getDependencies(archiveTask)
                    .any { task -> task.name == "fixtureBundleMemberValidation" },
            )
            assertFalse(Files.exists(expectedArchive))
        } finally {
            DistributionContractReaderTestSupport.deleteTree(projectDirectory)
        }
    }

    private fun repositoryRoot(): Path =
        generateSequence(Path.of("").toAbsolutePath().normalize()) { candidate -> candidate.parent }
            .firstOrNull { candidate ->
                Files.isRegularFile(
                    candidate.resolve(
                        "contract/src/main/resources/dev/erst/fingrind/contract/protocol/bundle-layout-contract.json",
                    ),
                )
            }
            ?: error("Could not locate the FinGrind repository root from the build-logic test process.")
}
