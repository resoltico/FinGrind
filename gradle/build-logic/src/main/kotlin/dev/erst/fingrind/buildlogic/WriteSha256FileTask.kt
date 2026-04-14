package dev.erst.fingrind.buildlogic

import java.security.MessageDigest
import java.util.HexFormat
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class WriteSha256FileTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeSha256File() {
        val input = inputFile.get().asFile
        val digest = MessageDigest.getInstance("SHA-256").digest(input.readBytes())
        val checksumLine =
            HexFormat.of().formatHex(digest) + "  " + input.name + System.lineSeparator()

        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(checksumLine)
    }
}
