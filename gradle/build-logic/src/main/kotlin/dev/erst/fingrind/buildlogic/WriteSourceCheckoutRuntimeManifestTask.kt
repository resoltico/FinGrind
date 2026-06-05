package dev.erst.fingrind.buildlogic

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class WriteSourceCheckoutRuntimeManifestTask : DefaultTask() {
    @get:Input
    abstract val ownerTaskName: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val javaExecutable: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val javaInstallationDirectory: DirectoryProperty

    @get:Input
    abstract val nativeAccessModule: Property<String>

    @get:Input
    abstract val applicationModule: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeManifest() {
        val executablePath = javaExecutable.get().asFile.toPath().toAbsolutePath().normalize()
        val installationPath =
            javaInstallationDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        require(Files.isRegularFile(executablePath)) {
            "Missing Java executable for source-checkout runtime manifest at $executablePath."
        }
        require(Files.isDirectory(installationPath)) {
            "Missing Java installation directory for source-checkout runtime manifest at $installationPath."
        }
        val manifestLines =
            listOf(
                "formatVersion=1",
                "ownerTask=${ownerTaskName.get()}",
                "javaExecutable\t$executablePath",
                "javaInstallationDirectory\t$installationPath",
                "nativeAccessModule\t${nativeAccessModule.get()}",
                "applicationModule\t${applicationModule.get()}",
                "",
            )
        val manifestPath = outputFile.get().asFile.toPath()
        Files.createDirectories(manifestPath.parent)
        Files.writeString(manifestPath, manifestLines.joinToString("\n"), StandardCharsets.UTF_8)
    }
}
