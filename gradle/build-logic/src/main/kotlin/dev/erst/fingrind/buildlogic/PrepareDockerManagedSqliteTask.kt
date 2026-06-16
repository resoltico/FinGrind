package dev.erst.fingrind.buildlogic

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

abstract class PrepareDockerManagedSqliteTask
    @Inject
    constructor(
        private val execOperations: ExecOperations,
    ) : DefaultTask() {
        @get:InputFile
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val sourceFile: RegularFileProperty

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val supportFiles: ConfigurableFileCollection

        @get:Input
        abstract val architectureId: Property<String>

        @get:Input
        abstract val sqliteVersion: Property<String>

        @get:Input
        abstract val requiredCompileOptions: ListProperty<String>

        @get:Input
        abstract val forbiddenCompileOptions: ListProperty<String>

        @get:Input
        abstract val requiresSecureMemorySupport: Property<Boolean>

        @get:Input
        abstract val unixCompilerHardeningFlags: ListProperty<String>

        @get:Input
        abstract val linuxLinkerHardeningFlags: ListProperty<String>

        @get:Input
        abstract val builderImage: Property<String>

        @get:Input
        abstract val buildBasePackage: Property<String>

        @get:Input
        abstract val pythonPackage: Property<String>

        @get:OutputFile
        abstract val toolchainFingerprintFile: RegularFileProperty

        @get:OutputFile
        abstract val buildContractFile: RegularFileProperty

        @get:OutputFile
        abstract val outputFile: RegularFileProperty

        @get:OutputFile
        abstract val checksumFile: RegularFileProperty

        @TaskAction
        fun compile() {
            val workspaceDirectory = Files.createTempDirectory("fingrind-docker-managed-sqlite-")
            try {
                val inputDirectory = workspaceDirectory.resolve("input")
                val exportDirectory = workspaceDirectory.resolve("export")
                val anonymousDockerConfigDirectory = workspaceDirectory.resolve("docker-config")
                Files.createDirectories(inputDirectory)
                Files.createDirectories(exportDirectory)
                Files.createDirectories(anonymousDockerConfigDirectory)
                Files.writeString(
                    anonymousDockerConfigDirectory.resolve("config.json"),
                    "{}\n",
                    StandardCharsets.UTF_8,
                )

                val stagedSourceFile = inputDirectory.resolve(sourceFile.get().asFile.name)
                sourceFile.get().asFile.copyTo(stagedSourceFile.toFile(), overwrite = true)
                supportFiles.files.forEach { supportFile ->
                    supportFile.copyTo(inputDirectory.resolve(supportFile.name).toFile(), overwrite = true)
                }

                val dockerPlatform =
                    DockerManagedSqliteBuildEnvironment.dockerPlatformForArchitecture(
                        architectureId.get(),
                    )
                val dockerCommand =
                    DockerManagedSqliteContainerBuildPlan.dockerRunCommand(
                        platform = dockerPlatform,
                        inputDirectory = inputDirectory,
                        outputDirectory = exportDirectory,
                        builderImage = builderImage.get(),
                        buildBasePackage = buildBasePackage.get(),
                        pythonPackage = pythonPackage.get(),
                        sourceFileName = sourceFile.get().asFile.name,
                        architectureId = architectureId.get(),
                        sqliteVersion = sqliteVersion.get(),
                        requiredCompileOptions = requiredCompileOptions.get(),
                        requiresSecureMemorySupport = requiresSecureMemorySupport.get(),
                        unixCompilerHardeningFlags = unixCompilerHardeningFlags.get(),
                        linuxLinkerHardeningFlags = linuxLinkerHardeningFlags.get(),
                    )

                logger.lifecycle(
                    "Preparing Docker-managed SQLite via {} on {} with anonymous Docker config {}",
                    builderImage.get(),
                    dockerPlatform,
                    anonymousDockerConfigDirectory,
                )
                execOperations.exec {
                    environment("DOCKER_CONFIG", anonymousDockerConfigDirectory.toString())
                    commandLine(dockerCommand)
                }

                val builtLibraryFile = exportDirectory.resolve("libsqlite3.so.0").toFile()
                val builtToolchainFingerprintFile =
                    exportDirectory.resolve("toolchain-fingerprint.json").toFile()
                require(builtLibraryFile.isFile) {
                    "Docker-managed SQLite build did not export ${builtLibraryFile.absolutePath}."
                }
                require(builtToolchainFingerprintFile.isFile) {
                    "Docker-managed SQLite build did not export ${builtToolchainFingerprintFile.absolutePath}."
                }

                val outputLibraryFile = outputFile.get().asFile
                outputLibraryFile.parentFile.mkdirs()
                builtLibraryFile.copyTo(outputLibraryFile, overwrite = true)
                val toolchainFile = toolchainFingerprintFile.get().asFile
                toolchainFile.parentFile.mkdirs()
                builtToolchainFingerprintFile.copyTo(toolchainFile, overwrite = true)
                ManagedSqliteArtifactSupport.writeBuildContractFile(
                    buildContractOutputFile = buildContractFile.get().asFile,
                    sqliteVersion = sqliteVersion.get(),
                    operatingSystemId = "linux",
                    requiredCompileOptions = requiredCompileOptions.get(),
                    forbiddenCompileOptions = forbiddenCompileOptions.get(),
                    requiresSecureMemorySupport = requiresSecureMemorySupport.get(),
                )
                ManagedSqliteArtifactSupport.writeChecksumFile(
                    outputLibraryFile = outputLibraryFile,
                    checksumOutputFile = checksumFile.get().asFile,
                )
            } finally {
                workspaceDirectory.toFile().deleteRecursively()
            }
        }
    }
