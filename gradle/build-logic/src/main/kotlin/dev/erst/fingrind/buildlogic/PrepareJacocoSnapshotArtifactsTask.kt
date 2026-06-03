package dev.erst.fingrind.buildlogic

import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class PrepareJacocoSnapshotArtifactsTask : DefaultTask() {
    @get:Input
    abstract val snapshotBaseVersion: Property<String>

    @get:Input
    abstract val resolvedVersion: Property<String>

    @get:OutputFile
    abstract val agentJarFile: RegularFileProperty

    @get:OutputFile
    abstract val antJarFile: RegularFileProperty

    @get:OutputFile
    abstract val coreJarFile: RegularFileProperty

    @get:OutputFile
    abstract val reportJarFile: RegularFileProperty

    @TaskAction
    fun prepare() {
        download(
            moduleId = "org.jacoco.agent",
            outputFile = agentJarFile.get().asFile.toPath(),
        )
        download(
            moduleId = "org.jacoco.ant",
            outputFile = antJarFile.get().asFile.toPath(),
        )
        download(
            moduleId = "org.jacoco.core",
            outputFile = coreJarFile.get().asFile.toPath(),
        )
        download(
            moduleId = "org.jacoco.report",
            outputFile = reportJarFile.get().asFile.toPath(),
        )
    }

    private fun download(
        moduleId: String,
        outputFile: java.nio.file.Path,
    ) {
        val versionDirectory = snapshotBaseVersion.get()
        val versionValue = resolvedVersion.get()
        val fileName = "$moduleId-$versionValue.jar"
        val artifactUri =
            URI(
                "https://central.sonatype.com/repository/maven-snapshots/" +
                    "org/jacoco/$moduleId/$versionDirectory/$fileName",
            )
        Files.createDirectories(outputFile.parent)
        val tempFile = Files.createTempFile(outputFile.parent, outputFile.fileName.toString(), ".part")
        try {
            artifactUri.toURL().openStream().use { inputStream ->
                Files.newOutputStream(tempFile).use(inputStream::copyTo)
            }
            Files.move(
                tempFile,
                outputFile,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }
}
