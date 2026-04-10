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
            execOperations.exec {
                commandLine(
                    buildCommandLine(
                        compiler = compiler.get(),
                        operatingSystemId = operatingSystemId.get(),
                        sqliteVersion = sqliteVersion.get(),
                        sourceFilePath = sourceFile.get().asFile.absolutePath,
                        outputFilePath = outputLibraryFile.absolutePath,
                    ),
                )
            }
        }

        private fun buildCommandLine(
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
    }
