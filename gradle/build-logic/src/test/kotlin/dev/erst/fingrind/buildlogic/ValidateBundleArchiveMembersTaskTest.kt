package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import org.gradle.kotlin.dsl.register
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.io.TempDir

class ValidateBundleArchiveMembersTaskTest {
    @TempDir lateinit var temporaryDirectory: Path

    @Test
    fun validatesEveryActualStagedDirectoryAndRuntimeMemberBeforeZipArchiving() {
        val project = ProjectBuilder.builder().withProjectDir(temporaryDirectory.toFile()).build()
        val archiveRootName = "fingrind-1.2.3-windows-x86_64"
        val bundleRootDirectory = temporaryDirectory.resolve(archiveRootName)
        Files.createDirectories(bundleRootDirectory.resolve("runtime/lib"))
        Files.createDirectories(bundleRootDirectory.resolve("lib/app"))
        Files.writeString(bundleRootDirectory.resolve("runtime/lib/modules"), "runtime image")
        Files.writeString(bundleRootDirectory.resolve("lib/app/fingrind.jar"), "application")

        val task =
            project.tasks
                .register<ValidateBundleArchiveMembersTask>("validateFixtureArchiveMembers")
                .get()
        task.bundleRootDirectory.set(project.layout.projectDirectory.dir(archiveRootName))
        task.archiveRootName.set(archiveRootName)
        task.archiveFormat.set("zip")

        task.validateBundleArchiveMembers()
    }

}
