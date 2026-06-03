package dev.erst.fingrind.buildlogic

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
            val workspaceDirectory = temporaryDir.resolve("docker-managed-sqlite").toPath()
            val contextDirectory = workspaceDirectory.resolve("context")
            val exportDirectory = workspaceDirectory.resolve("export")
            contextDirectory.toFile().deleteRecursively()
            exportDirectory.toFile().deleteRecursively()
            Files.createDirectories(contextDirectory)
            Files.createDirectories(exportDirectory)

            val stagedSourceFile = contextDirectory.resolve(sourceFile.get().asFile.name)
            sourceFile.get().asFile.copyTo(stagedSourceFile.toFile(), overwrite = true)
            supportFiles.files.forEach { supportFile ->
                supportFile.copyTo(contextDirectory.resolve(supportFile.name).toFile(), overwrite = true)
            }
            contextDirectory.resolve("Dockerfile").toFile().writeText(
                renderDockerfile(
                    architectureId = architectureId.get(),
                    sqliteVersion = sqliteVersion.get(),
                    requiredCompileOptions = requiredCompileOptions.get(),
                    requiresSecureMemorySupport = requiresSecureMemorySupport.get(),
                    unixCompilerHardeningFlags = unixCompilerHardeningFlags.get(),
                    linuxLinkerHardeningFlags = linuxLinkerHardeningFlags.get(),
                    builderImage = builderImage.get(),
                    buildBasePackage = buildBasePackage.get(),
                    pythonPackage = pythonPackage.get(),
                    sourceFileName = sourceFile.get().asFile.name,
                ),
            )

            execOperations.exec {
                commandLine(
                    "docker",
                    "buildx",
                    "build",
                    "--platform",
                    DockerManagedSqliteBuildEnvironment.dockerPlatformForArchitecture(
                        architectureId.get(),
                    ),
                    "--output",
                    "type=local,dest=${exportDirectory.toAbsolutePath()}",
                    contextDirectory.toAbsolutePath().toString(),
                )
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
        }

        private fun renderDockerfile(
            architectureId: String,
            sqliteVersion: String,
            requiredCompileOptions: List<String>,
            requiresSecureMemorySupport: Boolean,
            unixCompilerHardeningFlags: List<String>,
            linuxLinkerHardeningFlags: List<String>,
            builderImage: String,
            buildBasePackage: String,
            pythonPackage: String,
            sourceFileName: String,
        ): String {
            val compilerFlags =
                buildList {
                    add("cc")
                    add("-O2")
                    add("-fPIC")
                    addAll(unixCompilerHardeningFlags)
                    addAll(
                        ManagedSqliteArtifactSupport.unixCompilerDefines(
                            requiredCompileOptions,
                            requiresSecureMemorySupport,
                        ),
                    )
                    add("-shared")
                    add("-Wl,-soname,libsqlite3.so.0")
                    addAll(linuxLinkerHardeningFlags)
                    add("-o")
                    add("/work/libsqlite3.so.0")
                    add("/work/$sourceFileName")
                    add("-ldl")
                    add("-lpthread")
                }
            val packages = listOf(buildBasePackage, pythonPackage)
            val packagesJson =
                packages.joinToString(prefix = "[", postfix = "]") { packageName ->
                    quoted(packageName)
                }
            return """
                |FROM $builderImage
                |WORKDIR /work
                |RUN apk add --no-cache ${packages.joinToString(" ")}
                |COPY $sourceFileName sqlite3mc_amalgamation.h sqlite3.h sqlite3ext.h /work/
                |RUN ${compilerFlags.joinToString(" ") { shellQuote(it) }}
                |RUN python3 - <<'PY'
                |import json
                |import pathlib
                |import subprocess
                |
                |def run(*command: str) -> str:
                |    completed = subprocess.run(command, check=True, text=True, capture_output=True)
                |    return completed.stdout.strip()
                |
                |toolchain = {
                |    "compilerCommand": "cc",
                |    "compilerExecutable": run("sh", "-lc", "command -v cc"),
                |    "compilerVersion": run("cc", "--version"),
                |    "targetTriple": run("cc", "-dumpmachine"),
                |    "linkerVersion": run("ld", "--version"),
                |    "sdkOrSysroot": "",
                |    "operatingSystemId": "linux",
                |    "architectureId": ${quoted(architectureId)},
                |    "buildEnvironment": "docker-buildx",
                |    "builderImage": ${quoted(builderImage)},
                |    "packages": $packagesJson,
                |    "sqliteVersion": ${quoted(sqliteVersion)},
                |}
                |pathlib.Path("/work/toolchain-fingerprint.json").write_text(
                |    json.dumps(toolchain, indent=2) + "\n",
                |    encoding="utf-8",
                |)
                |PY
                |FROM scratch
                |COPY --from=0 /work/libsqlite3.so.0 /libsqlite3.so.0
                |COPY --from=0 /work/toolchain-fingerprint.json /toolchain-fingerprint.json
                |""".trimMargin()
        }

        private fun shellQuote(value: String): String =
            buildString {
                append('\'')
                value.forEach { character ->
                    if (character == '\'') {
                        append("'\"'\"'")
                    } else {
                        append(character)
                    }
                }
                append('\'')
            }

        private fun quoted(value: String): String =
            buildString {
                append('"')
                value.forEach { character ->
                    when (character) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> append(character)
                    }
                }
                append('"')
            }
    }
