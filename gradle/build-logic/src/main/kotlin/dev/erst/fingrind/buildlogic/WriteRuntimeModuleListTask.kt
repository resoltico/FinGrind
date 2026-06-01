package dev.erst.fingrind.buildlogic

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class WriteRuntimeModuleListTask : DefaultTask() {
    companion object {
        private val missingDependencyPattern = Regex("""->\s+([A-Za-z0-9_$.]+)\s+not found$""")
    }

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val javaHomeDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val applicationJar: RegularFileProperty

    @get:Input
    abstract val javaVersion: org.gradle.api.provider.Property<Int>

    @get:Input
    abstract val additionalModules: org.gradle.api.provider.ListProperty<String>

    @get:Input
    abstract val allowedMissingDependencyPrefixes: org.gradle.api.provider.ListProperty<String>

    @get:Classpath
    abstract val dependencyClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeRuntimeModuleList() {
        val outputPath = outputFile.get().asFile
        outputPath.parentFile.mkdirs()

        val jdepsExecutable = executable(javaHomeDirectory.get().asFile, "jdeps")
        val command =
            mutableListOf(
                jdepsExecutable.absolutePath,
                "--multi-release",
                javaVersion.get().toString(),
            )
        val classpathEntries = dependencyClasspath.files
        if (classpathEntries.isNotEmpty()) {
            command += "--class-path"
            command += classpathEntries.joinToString(File.pathSeparator) { file -> file.absolutePath }
        }
        val applicationJarPath = applicationJar.get().asFile.absolutePath
        val missingDependencies = missingDependencies(command, applicationJarPath)
        val moduleCommand = command.toMutableList()
        if (missingDependencies.isNotEmpty()) {
            moduleCommand += "--ignore-missing-deps"
        }
        moduleCommand += "--print-module-deps"
        moduleCommand += applicationJarPath
        val detectedModuleList =
            CommandLineRunner.run(moduleCommand)
                .trim()
        if (detectedModuleList.isEmpty()) {
            throw IllegalStateException(
                "jdeps produced an empty module list for $applicationJarPath.",
            )
        }
        val mergedModuleList =
            (detectedModuleList.split(',') + additionalModules.get())
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .sorted()
                .joinToString(",")
        outputPath.writeText(mergedModuleList + System.lineSeparator())
    }

    private fun missingDependencies(command: List<String>, applicationJarPath: String): List<String> {
        val missingDependencyOutput =
            CommandLineRunner.run(command + listOf("--missing-deps", applicationJarPath))
        val missingDependencies =
            missingDependencyOutput
                .lineSequence()
                .map(String::trim)
                .mapNotNull { line -> missingDependencyPattern.find(line)?.groupValues?.get(1) }
                .distinct()
                .sorted()
                .toList()
        if (missingDependencies.isEmpty()) {
            return emptyList()
        }
        val allowedPrefixes = allowedMissingDependencyPrefixes.get()
        val uncoveredDependencies =
            missingDependencies.filter { dependency ->
                allowedPrefixes.none { prefix -> dependency.startsWith(prefix) }
            }
        if (uncoveredDependencies.isNotEmpty()) {
            throw IllegalStateException(
                "jdeps reported missing dependencies outside the runtime-module discovery allowlist: " +
                    uncoveredDependencies.joinToString(", "),
            )
        }
        val unusedPrefixes =
            allowedPrefixes.filter { prefix ->
                missingDependencies.none { dependency -> dependency.startsWith(prefix) }
            }
        if (unusedPrefixes.isNotEmpty()) {
            throw IllegalStateException(
                "runtime-module discovery allowlist contains unused prefixes: " +
                    unusedPrefixes.joinToString(", "),
            )
        }
        return missingDependencies
    }

    private fun executable(javaHomeDirectory: File, executableName: String): File {
        val suffix = if (File.separatorChar == '\\') ".exe" else ""
        val executable = javaHomeDirectory.resolve("bin/$executableName$suffix")
        if (!executable.isFile) {
            throw IllegalStateException(
                "Expected $executableName under ${javaHomeDirectory.absolutePath}/bin but it was not found.",
            )
        }
        return executable
    }
}
