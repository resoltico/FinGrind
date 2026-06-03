package dev.erst.fingrind.buildlogic

import javax.inject.Inject
import org.gradle.StartParameter
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecOperations

internal fun Project.configureRootJazzerVerification() {
    val isWindows = System.getProperty("os.name").startsWith("Windows")
    val forwardedArguments =
        providers.provider { nestedGradlePassthroughArguments(gradle.startParameter) }

    tasks.register<RootJazzerCheckTask>("jazzerCheck") {
        description =
            "Runs deterministic Jazzer tests and regression replay through the nested Jazzer build."
        group = "verification"
        gradleWrapperFile.set(layout.projectDirectory.file(if (isWindows) "gradlew.bat" else "gradlew"))
        repositoryRootDirectory.set(layout.projectDirectory)
        jazzerProjectDirectory.set(layout.projectDirectory.dir("jazzer"))
        nestedGradleArguments.set(forwardedArguments)
    }
}

private fun nestedGradlePassthroughArguments(startParameter: StartParameter): List<String> =
    buildList {
        add("--no-daemon")
        add("--no-configuration-cache")
        add("--console=${startParameter.consoleOutput.name.lowercase()}")
        add("--warning-mode=${startParameter.warningMode.name.lowercase()}")
        when (startParameter.logLevel) {
            LogLevel.DEBUG -> add("--debug")
            LogLevel.INFO -> add("--info")
            LogLevel.WARN -> add("--warn")
            LogLevel.QUIET -> add("--quiet")
            else -> {}
        }
        when (startParameter.showStacktrace) {
            ShowStacktrace.ALWAYS -> add("--stacktrace")
            ShowStacktrace.ALWAYS_FULL -> add("--full-stacktrace")
            else -> {}
        }
        if (startParameter.isContinueOnFailure) {
            add("--continue")
        }
    }

abstract class RootJazzerCheckTask
    @Inject
    constructor(
        private val execOperations: ExecOperations,
    ) : DefaultTask() {
        init {
            doNotTrackState("Delegates verification to the nested Jazzer build.")
        }

        @get:InputFile
        abstract val gradleWrapperFile: RegularFileProperty

        @get:Internal
        abstract val repositoryRootDirectory: DirectoryProperty

        @get:InputDirectory
        abstract val jazzerProjectDirectory: DirectoryProperty

        @get:Input
        abstract val nestedGradleArguments: org.gradle.api.provider.ListProperty<String>

        @TaskAction
        fun runNestedJazzerCheck() {
            execOperations.exec {
                workingDir = repositoryRootDirectory.get().asFile
                commandLine(
                    buildList {
                        add(gradleWrapperFile.get().asFile.absolutePath)
                        add("-p")
                        add(jazzerProjectDirectory.get().asFile.absolutePath)
                        addAll(nestedGradleArguments.get())
                        add("clean")
                        add("check")
                    },
                )
            }
        }
    }
