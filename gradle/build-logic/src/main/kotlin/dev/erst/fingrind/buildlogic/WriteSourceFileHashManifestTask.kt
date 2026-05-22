package dev.erst.fingrind.buildlogic

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class WriteSourceFileHashManifestTask : DefaultTask() {
    @get:Input
    abstract val ownerTaskName: Property<String>

    @get:Input
    abstract val repositoryRootPath: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeManifest() {
        val repositoryRoot = Path.of(repositoryRootPath.get()).toAbsolutePath().normalize()
        val normalizedSourceFiles =
            sourceFiles.asFileTree.files
                .asSequence()
                .map { it.toPath().toAbsolutePath().normalize() }
                .filter { Files.isRegularFile(it) }
                .sorted()
                .toList()
        val manifestLines =
            buildList {
                add("formatVersion=1")
                add("ownerTask=${ownerTaskName.get()}")
                normalizedSourceFiles.forEach { sourceFile ->
                    val relativeSourceFile =
                        repositoryRoot.relativize(sourceFile).toString().replace('\\', '/')
                    add("sourceFile\t$relativeSourceFile\t${sha256Of(sourceFile)}")
                }
                add("")
            }
        val manifestPath = outputFile.get().asFile.toPath()
        Files.createDirectories(manifestPath.parent)
        Files.writeString(manifestPath, manifestLines.joinToString("\n"), StandardCharsets.UTF_8)
    }

    private fun sha256Of(sourceFile: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(Files.readAllBytes(sourceFile))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
