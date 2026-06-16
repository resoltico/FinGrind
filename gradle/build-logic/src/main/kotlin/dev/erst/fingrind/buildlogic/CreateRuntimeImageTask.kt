package dev.erst.fingrind.buildlogic

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class CreateRuntimeImageTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val javaExecutable: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val javaInstallationDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val runtimeModuleListFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun createRuntimeImage() {
        val javaHome = javaInstallationDirectory.get().asFile
        val javaExecutableFile = javaExecutable.get().asFile
        require(javaExecutableFile.isFile) {
            "Expected Java executable at ${javaExecutableFile.absolutePath} but it was not found."
        }
        val jlinkExecutable = executable(javaHome, "jlink")
        val jmodsDirectory = javaHome.resolve("jmods")
        if (!jmodsDirectory.isDirectory) {
            throw IllegalStateException(
                "Expected jmods under ${javaHome.absolutePath}/jmods but it was not found.",
            )
        }

        val moduleList = runtimeModuleListFile.get().asFile.readText().trim()
        if (moduleList.isEmpty()) {
            throw IllegalStateException(
                "Runtime module list is empty at ${runtimeModuleListFile.get().asFile.absolutePath}.",
            )
        }

        val runtimeDirectory = outputDirectory.get().asFile
        runtimeDirectory.deleteRecursively()
        runtimeDirectory.parentFile.mkdirs()

        CommandLineRunner.run(
            listOf(
                jlinkExecutable.absolutePath,
                "--module-path",
                jmodsDirectory.absolutePath,
                "--add-modules",
                moduleList,
                "--strip-debug",
                "--no-header-files",
                "--no-man-pages",
                "--compress=zip-6",
                "--output",
                runtimeDirectory.absolutePath,
            ),
        )
    }

    private fun executable(javaHomeDirectory: File, executableName: String): File {
        val suffix = if (File.separatorChar == '\\') ".exe" else ""
        val executable = javaHomeDirectory.resolve("bin/$executableName$suffix")
        if (!executable.isFile) {
            throw IllegalStateException(
                "Expected $executableName under ${javaHomeDirectory.absolutePath}/bin but it was not found.",
            )
        }
        return executable
    }
}
