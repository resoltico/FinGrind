package dev.erst.fingrind.buildlogic

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class WriteDockerBuildContextManifestTask : DefaultTask() {
    @get:Input
    abstract val ownerTaskName: Property<String>

    @get:Input
    abstract val fileNames: ListProperty<String>

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
        val relativeSourceFiles =
            normalizedSourceFiles.map { sourceFile ->
                repositoryRoot.relativize(sourceFile).toString().replace('\\', '/')
            }
        val sourceFingerprintSha3 = sourceFingerprint(relativeSourceFiles, normalizedSourceFiles)
        val manifestLines =
            buildList {
                add("{")
                add("""  "formatVersion": 2,""")
                add("""  "ownerTask": "${escapeJson(ownerTaskName.get())}",""")
                add("""  "sourceFingerprintSha3": "$sourceFingerprintSha3",""")
                add("""  "sourceFiles": [""")
                relativeSourceFiles.forEachIndexed { index, relativeSourceFile ->
                    add(
                        """    "${escapeJson(relativeSourceFile)}"${if (index == relativeSourceFiles.lastIndex) "" else ","}""",
                    )
                }
                add("  ],")
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

    private fun sourceFingerprint(relativeSourceFiles: List<String>, sourceFiles: List<java.nio.file.Path>): String {
        val digest = MessageDigest.getInstance("SHA3-256")
        relativeSourceFiles.zip(sourceFiles).forEach { (relativeSourceFile, sourceFile) ->
            digest.update(relativeSourceFile.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(Files.readAllBytes(sourceFile))
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
