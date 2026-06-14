package dev.erst.fingrind.buildlogic

import java.io.File
import java.nio.charset.StandardCharsets
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class WriteBundleArchiveManifestTask : DefaultTask() {
    @get:InputFile
    abstract val archiveFile: RegularFileProperty

    @get:InputFile
    abstract val checksumFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeManifest() {
        val archivePath = normalizedPath(archiveFile.get().asFile)
        val checksumPath = normalizedPath(checksumFile.get().asFile)
        val manifestPath = normalizedPath(outputFile.get().asFile)
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                buildString {
                    appendLine("{")
                    appendLine("  \"archivePath\": ${json(archivePath)},")
                    appendLine("  \"checksumPath\": ${json(checksumPath)}")
                    appendLine("}")
                },
                StandardCharsets.UTF_8,
            )
        }
        logger.lifecycle("FinGrind CLI bundle archive: $archivePath")
        logger.lifecycle("FinGrind CLI bundle checksum: $checksumPath")
        logger.lifecycle("FinGrind CLI bundle manifest: $manifestPath")
    }

    private fun normalizedPath(file: File): String = file.path.replace(File.separatorChar, '/')

    private fun json(value: String): String =
        buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
            append('"')
        }
}
