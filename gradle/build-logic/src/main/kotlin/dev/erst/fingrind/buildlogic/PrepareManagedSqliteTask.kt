package dev.erst.fingrind.buildlogic
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
            ManagedSqliteArtifactSupport.writeBuildContractFile(
                buildContractOutputFile = buildContractFile.get().asFile,
                sqliteVersion = sqliteVersion.get(),
                operatingSystemId = operatingSystemId.get(),
                requiredCompileOptions = requiredCompileOptions.get(),
                forbiddenCompileOptions = forbiddenCompileOptions.get(),
                requiresSecureMemorySupport = requiresSecureMemorySupport.get(),
            )
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
            ManagedSqliteArtifactSupport.writeChecksumFile(
                outputLibraryFile,
                checksumFile.get().asFile,
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
                    WindowsManagedSqliteCompilePlan.commandLine(
                        compiler = compiler,
                        sourceFilePath = sourceFilePath,
                        requiredCompileOptions = requiredCompileOptions.get(),
                        requiresSecureMemorySupport = requiresSecureMemorySupport,
                        compilerHardeningFlags = windowsCompilerHardeningFlags,
                        linkerHardeningFlags = windowsLinkerHardeningFlags,
                        outputLibraryFilePath = compiledLibraryFile.absolutePath,
                        importLibraryFilePath = importLibraryFile.absolutePath,
                        objectFilePath = objectFile.absolutePath,
                    ),
                )
            }
            compiledLibraryFile.copyTo(outputLibraryFile, overwrite = true)
            ManagedSqliteArtifactSupport.writeBuildContractFile(
                buildContractOutputFile = buildContractFile.get().asFile,
                sqliteVersion = sqliteVersion.get(),
                operatingSystemId = operatingSystemId.get(),
                requiredCompileOptions = requiredCompileOptions.get(),
                forbiddenCompileOptions = forbiddenCompileOptions.get(),
                requiresSecureMemorySupport = requiresSecureMemorySupport,
            )
            ManagedSqliteArtifactSupport.writeChecksumFile(
                outputLibraryFile,
                checksumFile.get().asFile,
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

        private fun unixCompilerDefines(
            requiredCompileOptions: List<String>,
            requiresSecureMemorySupport: Boolean,
        ): List<String> =
            ManagedSqliteArtifactSupport.unixCompilerDefines(
                requiredCompileOptions,
                requiresSecureMemorySupport,
            )

    }
