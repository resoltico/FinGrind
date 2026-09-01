package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Preserves resolved runtime dependency legal resources byte-for-byte under collision-free paths.
 *
 * Dependency JARs commonly reuse META-INF/LICENSE and META-INF/NOTICE. A shaded JAR cannot retain
 * those at their original paths without duplicate ZIP entries, while choosing one or replacing
 * them with a generic license can discard controlling external-component terms. This task gives
 * every source artifact its own directory and records hashes for the artifact and each copied
 * resource.
 */
abstract class StageRuntimeLegalResourcesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val runtimeArtifacts: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val legalResourceLockFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun stage() {
        val outputRoot = outputDirectory.get().asFile.toPath()
        if (Files.exists(outputRoot)) {
            check(outputRoot.toFile().deleteRecursively()) {
                "Could not clear stale runtime legal resource output at $outputRoot"
            }
        }
        Files.createDirectories(outputRoot)

        val indexRows = mutableListOf<String>()
        val usedArtifactDirectories = mutableSetOf<String>()
        val stagedResourcePaths = mutableSetOf<java.nio.file.Path>()
        runtimeArtifacts.files
            .asSequence()
            .filter { artifact ->
                artifact.isFile && artifact.extension.equals("jar", ignoreCase = true)
            }
            .sortedBy { artifact -> artifact.name }
            .forEach { artifact ->
                val artifactDirectory = requirePortableArtifactDirectory(artifact.name)
                check(usedArtifactDirectories.add(artifactDirectory)) {
                    "Runtime legal resource artifact directory collision for " +
                        "${artifact.name}: $artifactDirectory"
                }
                val artifactDigest = sha256(artifact.toPath())
                ZipFile(artifact).use { archive ->
                    val legalEntries =
                        archive.entries()
                            .asSequence()
                            .filterNot { entry -> entry.isDirectory }
                            .filter { entry -> isLegalResource(entry.name) }
                            .sortedBy { entry -> entry.name }
                            .toList()
                    check(legalEntries.isNotEmpty()) {
                        "Runtime dependency ${artifact.name} did not carry a reviewed legal resource."
                    }
                    legalEntries.forEach { entry ->
                        val relativeResource = requireSafeLegalResource(entry.name)
                        val target = outputRoot.resolve(artifactDirectory).resolve(relativeResource)
                        check(stagedResourcePaths.add(target)) {
                            "Runtime legal resources mapped more than once to $target."
                        }
                        Files.createDirectories(target.parent)
                        archive.getInputStream(entry).use { input ->
                            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                        }
                        indexRows +=
                            listOf(
                                    artifact.name,
                                    artifactDigest,
                                    entry.name,
                                    sha256(target),
                                )
                                .joinToString("\t")
                    }
                }
            }

        check(indexRows.isNotEmpty()) {
            "Resolved runtime classpath did not provide any dependency license or notice resources."
        }
        val renderedIndex =
            buildString {
                append("artifact\tartifact-sha256\tresource\tresource-sha256\n")
                indexRows.forEach { row -> append(row).append('\n') }
            }
        val expectedIndex = legalResourceLockFile.get().asFile.readText()
        check(renderedIndex == expectedIndex) {
            "Resolved runtime dependency legal resources differ from " +
                legalResourceLockFile.get().asFile.absolutePath +
                ". Review the changed artifacts and update the lock deliberately."
        }
        Files.writeString(outputRoot.resolve("INDEX.tsv"), renderedIndex)
    }

    private fun isLegalResource(entryName: String): Boolean {
        val normalizedEntryName = entryName.replace('\\', '/')
        val normalizedUpperName = normalizedEntryName.uppercase(Locale.ROOT)
        val fileName = normalizedEntryName.substringAfterLast('/').uppercase(Locale.ROOT)
        if (fileName.endsWith(".CLASS")) return false
        return "/LICENSES/" in "/$normalizedUpperName" ||
            "/LEGAL/" in "/$normalizedUpperName" ||
            fileName.contains("LICENSE") ||
            fileName.contains("NOTICE") ||
            fileName.startsWith("COPYING") ||
            fileName.startsWith("COPYRIGHT") ||
            fileName == "UNLICENSE" ||
            fileName.startsWith("THIRD-PARTY") ||
            fileName.startsWith("THIRDPARTY") ||
            fileName.startsWith("LEGAL")
    }

    private fun requireSafeLegalResource(entryName: String): String {
        val normalizedEntryName = entryName.replace('\\', '/')
        val relative =
            if (normalizedEntryName.startsWith("META-INF/", ignoreCase = true)) {
                normalizedEntryName.substringAfter("META-INF/")
            } else {
                normalizedEntryName
            }
        val path = java.nio.file.Path.of(relative).normalize()
        require(
            !path.isAbsolute &&
                path.nameCount > 0 &&
                path.none { part -> part.toString() == ".." },
        ) {
            "Unsafe runtime legal resource path in dependency JAR: $entryName"
        }
        return path.toString().replace('\\', '/')
    }

    private fun requirePortableArtifactDirectory(fileName: String): String {
        require(fileName.matches(Regex("[A-Za-z0-9._+-]+\\.jar"))) {
            "Runtime dependency JAR has a non-portable legal resource directory name: $fileName"
        }
        return fileName.removeSuffix(".jar")
    }

    private fun sha256(path: java.nio.file.Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}
