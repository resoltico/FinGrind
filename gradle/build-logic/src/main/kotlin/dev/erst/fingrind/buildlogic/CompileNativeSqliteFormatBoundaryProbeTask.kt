package dev.erst.fingrind.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Compiles the private unnamed-module probe that proves protected-book format boundaries. */
@CacheableTask
abstract class CompileNativeSqliteFormatBoundaryProbeTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val javaCompiler: RegularFileProperty

    @get:Input
    abstract val javaVersion: Property<Int>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun compileProbe() {
        val destinationDirectory = outputDirectory.get().asFile
        destinationDirectory.deleteRecursively()
        require(destinationDirectory.mkdirs()) {
            "Could not create native SQLite format-boundary probe output directory " +
                "${destinationDirectory.absolutePath}."
        }
        CommandLineRunner.run(
            listOf(
                javaCompiler.get().asFile.absolutePath,
                "--release",
                javaVersion.get().toString(),
                "-d",
                destinationDirectory.absolutePath,
                sourceFile.get().asFile.absolutePath,
            ),
        )
    }
}
