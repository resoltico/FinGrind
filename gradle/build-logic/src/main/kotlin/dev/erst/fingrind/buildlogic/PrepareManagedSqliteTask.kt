package dev.erst.fingrind.buildlogic

import java.security.MessageDigest
import java.util.HexFormat
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

@CacheableTask
abstract class PrepareManagedSqliteTask
    @Inject
    constructor(
        private val execOperations: ExecOperations,
    ) : DefaultTask() {
        init {
            outputs.upToDateWhen {
                outputFile.orNull?.asFile?.isFile == true && checksumFile.orNull?.asFile?.isFile == true
            }
        }

        @get:InputFile
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val sourceFile: RegularFileProperty

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.RELATIVE)
        abstract val supportFiles: ConfigurableFileCollection

        @get:Input
        abstract val compiler: Property<String>

        @get:Input
        abstract val operatingSystemId: Property<String>

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
        abstract val macosLinkerHardeningFlags: ListProperty<String>

        @get:Input
        abstract val windowsCompilerHardeningFlags: ListProperty<String>

        @get:Input
        abstract val windowsLinkerHardeningFlags: ListProperty<String>

        @get:InputFile
        @get:PathSensitive(PathSensitivity.NONE)
        abstract val toolchainFingerprintFile: RegularFileProperty

        @get:OutputFile
        abstract val buildContractFile: RegularFileProperty

        @get:OutputFile
        abstract val outputFile: RegularFileProperty

        @get:OutputFile
        abstract val checksumFile: RegularFileProperty

        @get:OutputFile
        abstract val trustedChecksumFile: RegularFileProperty

        @TaskAction
        fun compile() {
            val outputLibraryFile = outputFile.get().asFile
            outputLibraryFile.parentFile.mkdirs()
            outputLibraryFile.delete()
            val activeOperatingSystemId = operatingSystemId.get()
            if (activeOperatingSystemId == "windows") {
                compileWindowsLibrary(
                    compiler = compiler.get(),
                    sourceFilePath = sourceFile.get().asFile.absolutePath,
                    requiresSecureMemorySupport = requiresSecureMemorySupport.get(),
                    windowsCompilerHardeningFlags = windowsCompilerHardeningFlags.get(),
                    windowsLinkerHardeningFlags = windowsLinkerHardeningFlags.get(),
                    outputLibraryFile = outputLibraryFile,
                )
                return
            }
            writeBuildContractFile(buildContractFile.get().asFile)
            execOperations.exec {
                commandLine(
                    buildUnixCommandLine(
                        compiler = compiler.get(),
                        operatingSystemId = activeOperatingSystemId,
                        sqliteVersion = sqliteVersion.get(),
                        requiredCompileOptions = requiredCompileOptions.get(),
                        requiresSecureMemorySupport = requiresSecureMemorySupport.get(),
                        unixCompilerHardeningFlags = unixCompilerHardeningFlags.get(),
                        linuxLinkerHardeningFlags = linuxLinkerHardeningFlags.get(),
                        macosLinkerHardeningFlags = macosLinkerHardeningFlags.get(),
                        sourceFilePath = sourceFile.get().asFile.absolutePath,
                        outputFilePath = outputLibraryFile.absolutePath,
                    ),
                )
            }
            writeChecksumFiles(
                outputLibraryFile,
                checksumFile.get().asFile,
                trustedChecksumFile.get().asFile,
            )
        }

        private fun compileWindowsLibrary(
            compiler: String,
            sourceFilePath: String,
            requiresSecureMemorySupport: Boolean,
            windowsCompilerHardeningFlags: List<String>,
            windowsLinkerHardeningFlags: List<String>,
            outputLibraryFile: java.io.File,
        ) {
            val buildDirectory = temporaryDir.resolve("windows-shared-library")
            buildDirectory.deleteRecursively()
            buildDirectory.mkdirs()
            val compiledLibraryFile = buildDirectory.resolve(outputLibraryFile.name)
            val importLibraryFile = buildDirectory.resolve("sqlite3.lib")
            val objectFile = buildDirectory.resolve("sqlite3.obj")
            execOperations.exec {
                commandLine(
                    buildWindowsCommandLine(
                        compiler = compiler,
                        sourceFilePath = sourceFilePath,
                        requiredCompileOptions = requiredCompileOptions.get(),
                        requiresSecureMemorySupport = requiresSecureMemorySupport,
                        windowsCompilerHardeningFlags = windowsCompilerHardeningFlags,
                        windowsLinkerHardeningFlags = windowsLinkerHardeningFlags,
                        outputFilePath = compiledLibraryFile.absolutePath,
                        importLibraryFilePath = importLibraryFile.absolutePath,
                        objectFilePath = objectFile.absolutePath,
                    ),
                )
            }
            compiledLibraryFile.copyTo(outputLibraryFile, overwrite = true)
            writeBuildContractFile(buildContractFile.get().asFile)
            writeChecksumFiles(
                outputLibraryFile,
                checksumFile.get().asFile,
                trustedChecksumFile.get().asFile,
            )
        }

        private fun buildUnixCommandLine(
            compiler: String,
            operatingSystemId: String,
            sqliteVersion: String,
            requiredCompileOptions: List<String>,
            requiresSecureMemorySupport: Boolean,
            unixCompilerHardeningFlags: List<String>,
            linuxLinkerHardeningFlags: List<String>,
            macosLinkerHardeningFlags: List<String>,
            sourceFilePath: String,
            outputFilePath: String,
        ): List<String> =
            buildList {
                add(compiler)
                add("-O2")
                add("-fPIC")
                addAll(unixCompilerHardeningFlags)
                addAll(unixCompilerDefines(requiredCompileOptions, requiresSecureMemorySupport))
                if (operatingSystemId == "macos") {
                    add("-dynamiclib")
                    add("-current_version")
                    add(sqliteVersion)
                    add("-compatibility_version")
                    add(sqliteVersion)
                    addAll(macosLinkerHardeningFlags)
                } else {
                    add("-shared")
                    add("-Wl,-soname,libsqlite3.so.0")
                    addAll(linuxLinkerHardeningFlags)
                }
                add("-o")
                add(outputFilePath)
                add(sourceFilePath)
                if (operatingSystemId == "linux") {
                    add("-ldl")
                    add("-lpthread")
                }
            }

        private fun buildWindowsCommandLine(
            compiler: String,
            sourceFilePath: String,
            requiredCompileOptions: List<String>,
            requiresSecureMemorySupport: Boolean,
            windowsCompilerHardeningFlags: List<String>,
            windowsLinkerHardeningFlags: List<String>,
            outputFilePath: String,
            importLibraryFilePath: String,
            objectFilePath: String,
        ): List<String> =
            listOf(
                compiler,
                "/nologo",
                "/O2",
                *windowsCompilerHardeningFlags.toTypedArray(),
                "/LD",
                *windowsCompilerDefines(requiredCompileOptions, requiresSecureMemorySupport)
                    .toTypedArray(),
                "/DSQLITE_API=__declspec(dllexport)",
                "/Fo\"$objectFilePath\"",
                sourceFilePath,
                "/link",
                "/NOLOGO",
                "/INCREMENTAL:NO",
                *windowsLinkerHardeningFlags.toTypedArray(),
                "/OUT:\"$outputFilePath\"",
                "/IMPLIB:\"$importLibraryFilePath\"",
            )

        private fun unixCompilerDefines(
            requiredCompileOptions: List<String>,
            requiresSecureMemorySupport: Boolean,
        ): List<String> =
            compilerDefines(requiredCompileOptions, requiresSecureMemorySupport) {
                option -> "-D$option"
            }

        private fun windowsCompilerDefines(
            requiredCompileOptions: List<String>,
            requiresSecureMemorySupport: Boolean,
        ): List<String> =
            compilerDefines(requiredCompileOptions, requiresSecureMemorySupport) {
                option -> "/D$option"
            }

        private fun compilerDefines(
            requiredCompileOptions: List<String>,
            requiresSecureMemorySupport: Boolean,
            flagBuilder: (String) -> String,
        ): List<String> =
            requiredCompileOptions.map { option ->
                val normalized = option.trim()
                require(normalized.isNotEmpty()) {
                    "Managed SQLite compile options must not be blank."
                }
                val sqliteOption =
                    if (normalized.contains("=")) {
                        normalized
                    } else {
                        "$normalized=1"
                    }
                flagBuilder("SQLITE_$sqliteOption")
            } + listOfNotNull(
                if (requiresSecureMemorySupport) {
                    flagBuilder("SQLITE3MC_SECURE_MEMORY=1")
                } else {
                    null
                },
            )

        private fun writeChecksumFiles(
            outputLibraryFile: java.io.File,
            checksumOutputFile: java.io.File,
            trustedChecksumOutputFile: java.io.File,
        ) {
            val digest = MessageDigest.getInstance("SHA-256").digest(outputLibraryFile.readBytes())
            writeChecksumFile(outputLibraryFile, checksumOutputFile, digest)
            writeChecksumFile(outputLibraryFile, trustedChecksumOutputFile, digest)
        }

        private fun writeBuildContractFile(buildContractOutputFile: java.io.File) {
            buildContractOutputFile.parentFile.mkdirs()
            buildContractOutputFile.writeText(
                buildString {
                    appendLine("{")
                    appendLine("  \"sqliteVersion\": ${json(sqliteVersion.get())},")
                    appendLine("  \"operatingSystemId\": ${json(operatingSystemId.get())},")
                    appendLine("  \"requiredCompileOptions\": ${jsonArray(requiredCompileOptions.get())},")
                    appendLine("  \"forbiddenCompileOptions\": ${jsonArray(forbiddenCompileOptions.get())},")
                    appendLine(
                        "  \"requiresSecureMemorySupport\": ${requiresSecureMemorySupport.get()}",
                    )
                    appendLine("}")
                },
            )
        }

        private fun writeChecksumFile(
            outputLibraryFile: java.io.File,
            checksumOutputFile: java.io.File,
            digest: ByteArray,
        ) {
            checksumOutputFile.parentFile.mkdirs()
            checksumOutputFile.writeText(
                HexFormat.of().formatHex(digest) + "  " + outputLibraryFile.name + System.lineSeparator(),
            )
        }

        private fun jsonArray(values: List<String>): String =
            values.joinToString(prefix = "[", postfix = "]") { value -> json(value) }

        private fun json(value: String): String =
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
