package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class PruneBundleOutputsTask : DefaultTask() {
    @get:Input
    abstract val artifactPrefix: Property<String>

    @get:Internal
    abstract val bundleWorkspaceDirectory: DirectoryProperty

    @get:Internal
    abstract val bundleRootDirectory: DirectoryProperty

    @get:Internal
    abstract val distributionDirectory: DirectoryProperty

    @get:Internal
    abstract val legacyBundleWorkspaceDirectory: DirectoryProperty

    @get:Internal
    abstract val legacyDistributionDirectory: DirectoryProperty

    @TaskAction
    fun prune() {
        val prefix = artifactPrefix.get()
        deleteDirectoryIfPresent(bundleRootDirectory.asFile.orNull)
        deletePrefixedEntries(bundleWorkspaceDirectory.asFile.orNull, prefix)
        deletePrefixedEntries(distributionDirectory.asFile.orNull, prefix)
        deletePrefixedEntries(legacyBundleWorkspaceDirectory.asFile.orNull, prefix)
        deletePrefixedEntries(legacyDistributionDirectory.asFile.orNull, prefix)
    }

    private fun deletePrefixedEntries(directory: java.io.File?, prefix: String) {
        if (directory == null || !directory.isDirectory) {
            return
        }
        Files.list(directory.toPath()).use { entries ->
            entries
                .filter { entry -> entry.fileName.toString().startsWith(prefix) }
                .forEach { entry -> deletePathIfPresent(entry.toFile()) }
        }
    }

    private fun deleteDirectoryIfPresent(directory: java.io.File?) {
        if (directory == null || !directory.exists()) {
            return
        }
        deletePathIfPresent(directory)
    }

    private fun deletePathIfPresent(path: java.io.File) {
        if (!path.exists()) {
            return
        }
        if (!path.deleteRecursively() && path.exists()) {
            throw GradleException("failed to delete stale bundle output ${path.absolutePath}")
        }
    }
}
