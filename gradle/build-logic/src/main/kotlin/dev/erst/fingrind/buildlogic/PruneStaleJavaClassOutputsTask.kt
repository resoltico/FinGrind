package dev.erst.fingrind.buildlogic

import java.io.File
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Prunes stale Java class outputs that no longer belong to the current source-owned class surface.
 *
 * <p>The task tracks source owners by relative source path stem. On normal builds it prunes only
 * owners whose source changed or disappeared since the previous successful inventory. When the
 * operator requests `--rerun-tasks`, the task additionally prunes every current owner so a forced
 * rebuild heals stray nested-class leftovers without deleting the whole destination directory.
 */
abstract class PruneStaleJavaClassOutputsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectories: ConfigurableFileCollection

    @get:LocalState
    abstract val classesDirectory: DirectoryProperty

    @get:OutputFile
    abstract val sourceOwnerManifest: RegularFileProperty

    @get:Input
    abstract val rerunTasksRequested: org.gradle.api.provider.Property<Boolean>

    @TaskAction
    fun prune() {
        val currentOwners = currentSourceOwners()
        val previousOwners = loadManifest()
        val ownersToPrune =
            if (rerunTasksRequested.get()) {
                (previousOwners.keys + currentOwners.keys).toSortedSet()
            } else {
                previousOwners.keys
                    .filter { ownerBasePath -> previousOwners[ownerBasePath] != currentOwners[ownerBasePath] }
                    .toSortedSet()
            }
        val classesRoot = classesDirectory.asFile.get()
        ownersToPrune.forEach { ownerBasePath -> pruneOwnerOutputs(classesRoot, ownerBasePath) }
        writeManifest(currentOwners)
    }

    private fun currentSourceOwners(): Map<String, String> {
        val owners = linkedMapOf<String, String>()
        sourceDirectories.files
            .filter(File::exists)
            .sortedBy(File::getAbsolutePath)
            .forEach { sourceDirectory ->
                require(sourceDirectory.isDirectory) {
                    "Expected Java source directory but found '${sourceDirectory.absolutePath}'."
                }
                sourceDirectory.walkTopDown()
                    .filter { sourceFile -> sourceFile.isFile && sourceFile.extension == "java" }
                    .forEach { sourceFile ->
                        val ownerBasePath =
                            sourceFile.relativeTo(sourceDirectory).invariantSeparatorsPath
                                .removeSuffix(".java")
                        val sourceHash = sha256(sourceFile)
                        val priorHash = owners.putIfAbsent(ownerBasePath, sourceHash)
                        require(priorHash == null) {
                            "Duplicate Java source owner '${ownerBasePath}' across configured source directories."
                        }
                    }
            }
        return owners.toSortedMap()
    }

    private fun loadManifest(): Map<String, String> {
        val manifestFile = sourceOwnerManifest.asFile.get()
        if (!manifestFile.isFile) {
            return emptyMap()
        }
        return buildMap {
            manifestFile.forEachLine { line ->
                if (line.isBlank()) {
                    return@forEachLine
                }
                val separatorIndex = line.indexOf('\t')
                require(separatorIndex > 0 && separatorIndex < line.length - 1) {
                    "Malformed stale-class owner manifest entry in '${manifestFile.absolutePath}': ${line}"
                }
                val ownerBasePath = line.substring(0, separatorIndex)
                val sourceHash = line.substring(separatorIndex + 1)
                put(ownerBasePath, sourceHash)
            }
        }
    }

    private fun pruneOwnerOutputs(classesRoot: File, ownerBasePath: String) {
        val ownerClassFile = classesRoot.resolve("${ownerBasePath}.class")
        val ownerDirectory = ownerClassFile.parentFile ?: return
        if (!ownerDirectory.isDirectory) {
            return
        }
        val ownerFileName = ownerClassFile.nameWithoutExtension
        ownerDirectory.listFiles()
            ?.asSequence()
            ?.filter(File::isFile)
            ?.filter { candidate ->
                candidate.name == "${ownerFileName}.class" ||
                    (candidate.name.startsWith("${ownerFileName}$") &&
                        candidate.name.endsWith(".class"))
            }
            ?.forEach { classFile ->
                require(classFile.delete()) {
                    "Unable to prune stale Java class output '${classFile.absolutePath}'."
                }
            }
    }

    private fun writeManifest(currentOwners: Map<String, String>) {
        val manifestFile = sourceOwnerManifest.asFile.get()
        manifestFile.parentFile.mkdirs()
        manifestFile.writeText(
            buildString {
                currentOwners.forEach { (ownerBasePath, sourceHash) ->
                    append(ownerBasePath)
                    append('\t')
                    append(sourceHash)
                    append('\n')
                }
            },
        )
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) {
                    break
                }
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
