package dev.erst.fingrind.buildlogic

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class CreateRuntimeImageTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val javaExecutable: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val javaInstallationDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val runtimeModuleListFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun createRuntimeImage() {
        val javaHome = javaInstallationDirectory.get().asFile
        val javaExecutableFile = javaExecutable.get().asFile
        require(javaExecutableFile.isFile) {
            "Expected Java executable at ${javaExecutableFile.absolutePath} but it was not found."
        }
        val jlinkExecutable = executable(javaHome, "jlink")
        val jmodsDirectory = javaHome.resolve("jmods")
        if (!jmodsDirectory.isDirectory) {
            throw IllegalStateException(
                "Expected jmods under ${javaHome.absolutePath}/jmods but it was not found.",
            )
        }

        val moduleList = runtimeModuleListFile.get().asFile.readText().trim()
        if (moduleList.isEmpty()) {
            throw IllegalStateException(
                "Runtime module list is empty at ${runtimeModuleListFile.get().asFile.absolutePath}.",
            )
        }

        val runtimeDirectory = outputDirectory.get().asFile
        if (runtimeDirectory.exists()) {
            check(runtimeDirectory.deleteRecursively()) {
                "Could not clear stale runtime image at ${runtimeDirectory.absolutePath}."
            }
        }
        runtimeDirectory.parentFile.mkdirs()

        CommandLineRunner.run(
            listOf(
                jlinkExecutable.absolutePath,
                "--module-path",
                jmodsDirectory.absolutePath,
                "--add-modules",
                moduleList,
                "--strip-debug",
                "--no-header-files",
                "--no-man-pages",
                "--compress=zip-6",
                "--output",
                runtimeDirectory.absolutePath,
            ),
        )
        writeRuntimeProvenance(javaHome, runtimeDirectory, moduleList)
        writeRuntimeLegalIndex(runtimeDirectory, moduleList)
    }

    private fun writeRuntimeProvenance(
        javaHome: File,
        runtimeDirectory: File,
        moduleList: String,
    ) {
        val sourceJdkRelease = javaHome.resolve("release")
        require(sourceJdkRelease.isFile) {
            "Expected source JDK release metadata at ${sourceJdkRelease.absolutePath}."
        }
        val provenanceDirectory = runtimeDirectory.resolve("provenance").toPath()
        Files.createDirectories(provenanceDirectory)
        Files.copy(
            sourceJdkRelease.toPath(),
            provenanceDirectory.resolve("source-jdk-release"),
            StandardCopyOption.REPLACE_EXISTING,
        )
        Files.writeString(
            provenanceDirectory.resolve("requested-modules.txt"),
            moduleList.trim() + "\n",
        )
    }

    private fun writeRuntimeLegalIndex(runtimeDirectory: File, moduleList: String) {
        val legalDirectory = runtimeDirectory.resolve("legal").toPath()
        require(Files.isDirectory(legalDirectory)) {
            "jlink runtime omitted its legal directory at $legalDirectory."
        }
        moduleList
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .forEach { moduleName ->
                require(Files.isDirectory(legalDirectory.resolve(moduleName))) {
                    "jlink runtime omitted legal material for selected module $moduleName."
                }
            }
        val legalFiles =
            Files.walk(legalDirectory).use { paths ->
                paths
                    .filter { path ->
                        Files.isRegularFile(path) && path.fileName.toString() != "INDEX.sha256"
                    }
                    .sorted()
                    .toList()
            }
        require(legalFiles.isNotEmpty()) {
            "jlink runtime legal directory was empty at $legalDirectory."
        }
        val realLegalDirectory = legalDirectory.toRealPath()
        legalFiles
            .filter(Files::isSymbolicLink)
            .forEach { legalLink ->
                require(legalLink.toRealPath().startsWith(realLegalDirectory)) {
                    "jlink runtime legal symlink escaped its legal tree: $legalLink"
                }
            }
        val index =
            buildString {
                legalFiles.forEach { legalFile ->
                    val relativePath =
                        legalDirectory.relativize(legalFile).toString().replace('\\', '/')
                    append(sha256(legalFile)).append("  ").append(relativePath).append('\n')
                }
            }
        Files.writeString(legalDirectory.resolve("INDEX.sha256"), index)
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
