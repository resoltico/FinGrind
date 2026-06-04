package dev.erst.fingrind.buildlogic

import java.io.File
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register

internal data class JazzerWhiteBoxModulePatch(
    val patchDirectory: Provider<Directory>,
    val patchTask: TaskProvider<Sync>,
)

internal fun Project.repositoryProjectOutputDirectoryForJazzer(
    projectSegment: String,
    relativeOutputPath: String,
): File = repositoryProjectBuildRootForJazzer().resolve("$projectSegment/$relativeOutputPath")

internal fun Project.registerJazzerWhiteBoxModulePatch(
    taskName: String,
    directoryName: String,
    packagePath: String,
    localOutputDirectories: List<Any> = emptyList(),
    repositoryOutputDirectories: List<File> = emptyList(),
): JazzerWhiteBoxModulePatch {
    val patchDirectory = layout.buildDirectory.dir(directoryName)
    val patchTask =
        tasks.register<Sync>(taskName) {
            // Rebuild these patch directories every time so white-box verification heals cached
            // archive drift and never reuses stale module-private helper state.
            outputs.upToDateWhen { false }
            localOutputDirectories.forEach { outputDirectory ->
                from(outputDirectory) {
                    include("$packagePath/**")
                }
            }
            repositoryOutputDirectories.forEach { outputDirectory ->
                from(outputDirectory) {
                    include("$packagePath/**")
                }
            }
            into(patchDirectory)
        }
    return JazzerWhiteBoxModulePatch(patchDirectory, patchTask)
}

private fun Project.repositoryProjectBuildRootForJazzer(): File =
    providers.systemProperty("fingrind.gradle.project-build-root")
        .orNull
        ?.takeIf(String::isNotBlank)
        ?.let(::File)
        ?: gradle.startParameter.projectCacheDir?.resolve("project-build")
        ?: error("Missing project-build root for Jazzer white-box patch staging.")
