package dev.erst.fingrind.buildlogic

import java.nio.charset.StandardCharsets.UTF_8
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
        // Published checksum files are portable verification inputs, so their line ending is part
        // of the artifact contract rather than a property of the build host.
        val checksumLine =
            HexFormat.of().formatHex(digest) + "  " + input.name + "\n"

        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(checksumLine, UTF_8)
    }
}
