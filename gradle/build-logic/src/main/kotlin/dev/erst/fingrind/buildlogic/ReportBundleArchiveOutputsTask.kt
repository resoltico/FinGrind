package dev.erst.fingrind.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

abstract class ReportBundleArchiveOutputsTask : DefaultTask() {
    @get:InputFile
    abstract val archiveFile: RegularFileProperty

    @get:InputFile
    abstract val checksumFile: RegularFileProperty

    @TaskAction
    fun reportOutputs() {
        logger.lifecycle("FinGrind bundle archive: ${archiveFile.get().asFile}")
        logger.lifecycle("FinGrind bundle checksum: ${checksumFile.get().asFile}")
    }
}
