package dev.erst.fingrind.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class ReportBundleArchiveOutputsTask : DefaultTask() {
    @get:InputFile
    abstract val archiveFile: RegularFileProperty

    @get:InputFile
    abstract val checksumFile: RegularFileProperty

    @TaskAction
    fun reportOutputs() {
        val archivePath = archiveFile.get().asFile.path.replace(File.separatorChar, '/')
        val checksumPath = checksumFile.get().asFile.path.replace(File.separatorChar, '/')
        logger.quiet("FINGRIND_BUNDLE_ARCHIVE=$archivePath")
        logger.quiet("FINGRIND_BUNDLE_CHECKSUM=$checksumPath")
    }
}
