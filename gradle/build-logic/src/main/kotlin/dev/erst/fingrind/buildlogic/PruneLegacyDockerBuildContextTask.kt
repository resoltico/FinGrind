package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class PruneLegacyDockerBuildContextTask : DefaultTask() {
    @get:Internal
    abstract val activeDockerBuildContextDirectory: DirectoryProperty

    @get:Internal
    abstract val legacyDockerBuildContextDirectory: DirectoryProperty

    @TaskAction
    fun prune() {
        val activeDirectory = activeDockerBuildContextDirectory.asFile.get().absoluteFile.toPath().normalize()
        val legacyDirectory = legacyDockerBuildContextDirectory.asFile.get().absoluteFile.toPath().normalize()
        if (activeDirectory == legacyDirectory || !Files.exists(legacyDirectory)) {
            return
        }
        val quarantinedDirectory =
            legacyDirectory.resolveSibling(
                "${legacyDirectory.fileName}.legacy-do-not-use-${System.currentTimeMillis()}",
            )
        try {
            Files.move(legacyDirectory, quarantinedDirectory)
        } catch (exception: Exception) {
            throw GradleException(
                "failed to quarantine stale legacy Docker build context ${legacyDirectory.toAbsolutePath()}",
                exception,
            )
        }
        tryDeleteRecursively(quarantinedDirectory)
    }

    private fun tryDeleteRecursively(directory: Path) {
        try {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        } catch (exception: Exception) {
            logger.warn(
                "quarantined stale legacy Docker build context at ${directory.toAbsolutePath()} because recursive deletion failed",
                exception,
            )
        }
    }
}
