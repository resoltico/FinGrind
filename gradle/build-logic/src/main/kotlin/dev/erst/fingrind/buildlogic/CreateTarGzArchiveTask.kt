package dev.erst.fingrind.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class CreateTarGzArchiveTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @get:OutputFile
    abstract val archiveFile: RegularFileProperty

    @TaskAction
    fun createArchive() {
        val source = sourceDirectory.get().asFile
        val sourceParent = source.parentFile
        check(sourceParent != null) {
            "Source directory ${source.absolutePath} does not have a parent directory."
        }

        val archive = archiveFile.get().asFile
        archive.parentFile.mkdirs()
        archive.delete()

        CommandLineSupport.run(
            listOf(
                "tar",
                "-czf",
                archive.absolutePath,
                "-C",
                sourceParent.absolutePath,
                source.name,
            ),
        )
    }
}
