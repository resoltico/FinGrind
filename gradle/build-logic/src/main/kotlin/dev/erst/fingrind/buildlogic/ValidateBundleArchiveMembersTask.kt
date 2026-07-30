package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Admits the actual staged archive tree after all dynamic producers, including jlink, have run.
 *
 * The walk intentionally does not follow links. ZIP bundles reject symbolic links and Windows
 * reparse-equivalent entries; tar.gz bundles may retain a normal symbolic-link member but never a
 * special entry.
 */
abstract class ValidateBundleArchiveMembersTask : DefaultTask() {
    init {
        outputs.upToDateWhen { false }
    }

    // Do not let Gradle snapshot this tree before our no-follow security admission has run.
    @get:Internal
    abstract val bundleRootDirectory: DirectoryProperty

    @get:Input
    abstract val archiveRootName: Property<String>

    @get:Input
    abstract val archiveFormat: Property<String>

    @TaskAction
    fun validateBundleArchiveMembers() {
        val bundleRootPath = bundleRootDirectory.get().asFile.toPath()
        val configuredArchiveRootName = archiveRootName.get()
        val rootAttributes =
            Files.readAttributes(
                bundleRootPath,
                BasicFileAttributes::class.java,
                NOFOLLOW_LINKS,
        )
        require(rootAttributes.isDirectory) {
            "Bundle archive root must be a physical directory, not a link or special entry: " +
                "$bundleRootPath."
        }
        require(bundleRootPath.fileName.toString() == configuredArchiveRootName) {
            "Bundle archive root directory does not match its configured archive root name: " +
                "$bundleRootPath."
        }
        val checkedArchiveFormat =
            BundleStagingContractValidation.requireSupportedArchiveFormat(archiveFormat.get())
        val archiveMembers =
            Files.walk(bundleRootPath).use { paths ->
                paths
                    .filter { stagedPath -> stagedPath != bundleRootPath }
                    .map { stagedPath -> archiveMember(bundleRootPath, stagedPath) }
                    .toList()
                    .sortedBy(PortableArchiveMember::relativePath)
            }

        WindowsPortableArchivePathPolicy.requirePortableArchiveMembers(
            archiveRootName = configuredArchiveRootName,
            archiveFormat = checkedArchiveFormat,
            archiveMembers = archiveMembers,
            label = "Staged FinGrind bundle",
        )
    }

    private fun archiveMember(
        bundleRootPath: Path,
        stagedPath: Path,
    ): PortableArchiveMember {
        val attributes =
            Files.readAttributes(
                stagedPath,
                BasicFileAttributes::class.java,
                NOFOLLOW_LINKS,
            )
        val kind =
            when {
                attributes.isSymbolicLink -> PortableArchiveMemberKind.SYMBOLIC_LINK
                attributes.isRegularFile -> PortableArchiveMemberKind.REGULAR_FILE
                attributes.isDirectory -> PortableArchiveMemberKind.DIRECTORY
                else -> PortableArchiveMemberKind.SPECIAL
            }
        val relativePath =
            bundleRootPath.relativize(stagedPath).joinToString("/") { path -> path.toString() }
        return PortableArchiveMember(relativePath = relativePath, kind = kind)
    }
}
