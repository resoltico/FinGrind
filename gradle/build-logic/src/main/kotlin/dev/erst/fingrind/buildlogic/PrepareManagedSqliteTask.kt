package dev.erst.fingrind.buildlogic

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
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

        @get:OutputFile
        abstract val outputFile: RegularFileProperty

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
                    outputLibraryFile = outputLibraryFile,
                )
                return
            }
            execOperations.exec {
                commandLine(
                    buildUnixCommandLine(
                        compiler = compiler.get(),
                        operatingSystemId = activeOperatingSystemId,
                        sqliteVersion = sqliteVersion.get(),
                        sourceFilePath = sourceFile.get().asFile.absolutePath,
                        outputFilePath = outputLibraryFile.absolutePath,
                    ),
                )
            }
        }

        private fun compileWindowsLibrary(
            compiler: String,
            sourceFilePath: String,
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
                        outputFilePath = compiledLibraryFile.absolutePath,
                        importLibraryFilePath = importLibraryFile.absolutePath,
                        objectFilePath = objectFile.absolutePath,
                    ),
                )
            }
            compiledLibraryFile.copyTo(outputLibraryFile, overwrite = true)
        }

        private fun buildUnixCommandLine(
            compiler: String,
            operatingSystemId: String,
            sqliteVersion: String,
            sourceFilePath: String,
            outputFilePath: String,
        ): List<String> =
            buildList {
                add(compiler)
                add("-O2")
                add("-fPIC")
                add("-DSQLITE_THREADSAFE=1")
                add("-DSQLITE_OMIT_LOAD_EXTENSION=1")
                add("-DSQLITE_TEMP_STORE=3")
                add("-DSQLITE_SECURE_DELETE=1")
                if (operatingSystemId == "macos") {
                    add("-dynamiclib")
                    add("-current_version")
                    add(sqliteVersion)
                    add("-compatibility_version")
                    add(sqliteVersion)
                } else {
                    add("-shared")
                    add("-Wl,-soname,libsqlite3.so.0")
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
            outputFilePath: String,
            importLibraryFilePath: String,
            objectFilePath: String,
        ): List<String> =
            listOf(
                compiler,
                "/nologo",
                "/O2",
                "/LD",
                "/DSQLITE_THREADSAFE=1",
                "/DSQLITE_OMIT_LOAD_EXTENSION=1",
                "/DSQLITE_TEMP_STORE=3",
                "/DSQLITE_SECURE_DELETE=1",
                "/DSQLITE_API=__declspec(dllexport)",
                "/Fo\"$objectFilePath\"",
                sourceFilePath,
                "/link",
                "/NOLOGO",
                "/INCREMENTAL:NO",
                "/OUT:\"$outputFilePath\"",
                "/IMPLIB:\"$importLibraryFilePath\"",
            )
    }
