package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.TaskAction

abstract class CleanLocalFindingsTask : DefaultTask() {
    @get:LocalState
    abstract val runsDirectory: DirectoryProperty

    @TaskAction
    fun clean() {
        val runsPath = runsDirectory.asFile.orNull?.toPath() ?: return
        if (Files.exists(runsPath)) {
            LocalJazzerStateCleaner.deleteRunFindings(runsPath, logger::warn)
        }
    }
}
