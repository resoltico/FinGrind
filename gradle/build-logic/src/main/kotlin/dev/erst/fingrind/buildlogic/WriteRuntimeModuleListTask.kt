package dev.erst.fingrind.buildlogic

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class WriteRuntimeModuleListTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val javaHomeDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val applicationJar: RegularFileProperty

    @get:Input
    abstract val javaVersion: org.gradle.api.provider.Property<Int>

    @get:Classpath
    abstract val dependencyClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeRuntimeModuleList() {
        val outputPath = outputFile.get().asFile
        outputPath.parentFile.mkdirs()

        val jdepsExecutable = executable(javaHomeDirectory.get().asFile, "jdeps")
        val command =
            mutableListOf(
                jdepsExecutable.absolutePath,
                "--multi-release",
                javaVersion.get().toString(),
            )
        val classpathEntries = dependencyClasspath.files
        if (classpathEntries.isNotEmpty()) {
            command += "--class-path"
            command += classpathEntries.joinToString(File.pathSeparator) { file -> file.absolutePath }
        }
        command += "--print-module-deps"
        command += applicationJar.get().asFile.absolutePath
        val moduleList =
            CommandLineSupport.run(command)
                .trim()
        if (moduleList.isEmpty()) {
            throw IllegalStateException(
                "jdeps produced an empty module list for ${applicationJar.get().asFile.absolutePath}.",
            )
        }
        outputPath.writeText(moduleList + System.lineSeparator())
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
