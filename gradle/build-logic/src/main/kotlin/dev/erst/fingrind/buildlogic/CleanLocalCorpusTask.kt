package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.TaskAction

abstract class CleanLocalCorpusTask : DefaultTask() {
    @get:LocalState
    abstract val localDirectory: DirectoryProperty

    @TaskAction
    fun clean() {
        val localPath = localDirectory.asFile.orNull?.toPath() ?: return
        if (Files.exists(localPath)) {
            LocalJazzerStateCleaner.deleteGeneratedCorpora(localPath, logger::warn)
        }
    }
}
