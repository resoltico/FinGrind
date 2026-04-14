package dev.erst.fingrind.buildlogic

import java.io.File
import java.nio.charset.StandardCharsets
import org.gradle.api.GradleException

internal object CommandLineSupport {
    fun run(command: List<String>, workingDirectory: File? = null): String {
        require(command.isNotEmpty()) { "command must not be empty" }

        val process =
            ProcessBuilder(command)
                .directory(workingDirectory)
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.readAllBytes().toString(StandardCharsets.UTF_8)
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException(
                buildString {
                    append("Command failed with exit code ")
                    append(exitCode)
                    append(": ")
                    append(command.joinToString(" "))
                    val normalizedOutput = output.trim()
                    if (normalizedOutput.isNotEmpty()) {
                        append(System.lineSeparator())
                        append(normalizedOutput)
                    }
                },
            )
        }
        return output
    }
}
