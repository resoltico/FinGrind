package dev.erst.fingrind.buildlogic

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class WriteDockerBuildContextManifestTask : DefaultTask() {
    @get:Input
    abstract val ownerTaskName: Property<String>

    @get:Input
    abstract val fileNames: ListProperty<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeManifest() {
        val manifestLines =
            buildList {
                add("{")
                add("""  "formatVersion": 1,""")
                add("""  "ownerTask": "${escapeJson(ownerTaskName.get())}",""")
                add("""  "files": [""")
                fileNames.get().forEachIndexed { index, fileName ->
                    val suffix = if (index == fileNames.get().lastIndex) "" else ","
                    add("""    "${escapeJson(fileName)}"$suffix""")
                }
                add("  ]")
                add("}")
                add("")
            }
        val manifestPath = outputFile.get().asFile.toPath()
        Files.createDirectories(manifestPath.parent)
        Files.writeString(manifestPath, manifestLines.joinToString("\n"), StandardCharsets.UTF_8)
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
