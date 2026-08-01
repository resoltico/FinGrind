package dev.erst.fingrind.buildlogic

import java.io.File
import java.security.MessageDigest
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.tasks.compile.JavaCompile

/** Configuration-cache-safe task action that prunes only before its owning Java compiler runs. */
internal class PruneStaleJavaOutputsBeforeCompilationAction(
    private val sourceDirectories: List<File>,
    private val sourceOwnerManifest: File,
    private val rerunTasksRequested: Boolean,
) : Action<Task> {
    override fun execute(task: Task) {
        val javaCompile = task as? JavaCompile
            ?: throw IllegalArgumentException(
                "Stale Java class pruning requires a JavaCompile task, not '${task.path}'.",
            )
        StaleJavaClassOutputPruner(
                sourceDirectories = sourceDirectories,
                classesDirectory = javaCompile.destinationDirectory.get().asFile,
                sourceOwnerManifest = sourceOwnerManifest,
                rerunTasksRequested = rerunTasksRequested,
            )
            .prune()
    }
}

/**
 * Prunes stale Java class outputs immediately before a compiler invocation that Gradle has already
 * committed to execute.
 *
 * <p>Keeping this mutation inside the owning {@code JavaCompile} task is essential: a separate
 * task would mutate the compiler output directory outside Gradle's output model, allowing parallel
 * consumers to observe a half-pruned class tree. The source-owner manifest identifies the exact
 * top-level class and nested classes that a changed or removed source may have left behind.
 */
internal class StaleJavaClassOutputPruner(
    private val sourceDirectories: Iterable<File>,
    private val classesDirectory: File,
    private val sourceOwnerManifest: File,
    private val rerunTasksRequested: Boolean,
) {
    /** Prunes every changed owner, or every current owner for an explicitly forced rebuild. */
    fun prune() {
        val currentOwners = currentSourceOwners()
        val previousOwners = loadManifest()
        val ownersToPrune =
            if (rerunTasksRequested) {
                (previousOwners.keys + currentOwners.keys).toSortedSet()
            } else {
                previousOwners.keys
                    .filter { ownerBasePath -> previousOwners[ownerBasePath] != currentOwners[ownerBasePath] }
                    .toSortedSet()
            }
        ownersToPrune.forEach { ownerBasePath -> pruneOwnerOutputs(ownerBasePath) }
        writeManifest(currentOwners)
    }

    private fun currentSourceOwners(): Map<String, String> {
        val owners = linkedMapOf<String, String>()
        sourceDirectories
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
        if (!sourceOwnerManifest.isFile) {
            return emptyMap()
        }
        return buildMap {
            sourceOwnerManifest.forEachLine { line ->
                if (line.isBlank()) {
                    return@forEachLine
                }
                val separatorIndex = line.indexOf('\t')
                require(separatorIndex > 0 && separatorIndex < line.length - 1) {
                    "Malformed stale-class owner manifest entry in '${sourceOwnerManifest.absolutePath}': $line"
                }
                put(line.substring(0, separatorIndex), line.substring(separatorIndex + 1))
            }
        }
    }

    private fun pruneOwnerOutputs(ownerBasePath: String) {
        val ownerClassFile = classesDirectory.resolve("${ownerBasePath}.class")
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
                    (candidate.name.startsWith("${ownerFileName}$") && candidate.name.endsWith(".class"))
            }
            ?.forEach { classFile ->
                require(classFile.delete()) {
                    "Unable to prune stale Java class output '${classFile.absolutePath}'."
                }
            }
    }

    private fun writeManifest(currentOwners: Map<String, String>) {
        sourceOwnerManifest.parentFile.mkdirs()
        sourceOwnerManifest.writeText(
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
