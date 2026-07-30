package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.time.Instant
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Applies one normalized timestamp to the staged public bundle before archiving it. */
abstract class NormalizeBundleFileTimestampsTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val bundleRootDirectory: DirectoryProperty

    @get:Input
    abstract val normalizedArtifactEpochSeconds: Property<Long>

    @TaskAction
    fun normalizeTimestamps() {
        val bundleRootPath = bundleRootDirectory.get().asFile.toPath()
        val normalizedInstant = Instant.ofEpochSecond(normalizedArtifactEpochSeconds.get())
        val normalizedFileTime = FileTime.from(normalizedInstant)
        val allPaths = Files.walk(bundleRootPath).use { stream -> stream.toList() }
        val files = allPaths.filterNot(::isDirectoryWithoutFollowingLinks)
        val directories =
            allPaths
                .filter(::isDirectoryWithoutFollowingLinks)
                .sortedByDescending(Path::getNameCount)

        (files + directories).forEach { path ->
            setLastModifiedTimeWithoutFollowingLinks(path, normalizedFileTime)
        }
    }

    private fun isDirectoryWithoutFollowingLinks(path: Path): Boolean =
        Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS).isDirectory

    private fun setLastModifiedTimeWithoutFollowingLinks(
        path: Path,
        normalizedFileTime: FileTime,
    ) {
        val view =
            requireNotNull(
                Files.getFileAttributeView(
                    path,
                    BasicFileAttributeView::class.java,
                    NOFOLLOW_LINKS,
                ),
            ) {
                "Cannot set the staged bundle timestamp without following links: $path."
            }
        view.setTimes(normalizedFileTime, null, null)
    }
}
