package dev.erst.fingrind.buildlogic

import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.register

/** The archive-native probe staged into both bundle and Docker release-smoke surfaces. */
internal data class NativeSqliteFormatBoundaryProbe(
    val packageTask: TaskProvider<Zip>,
    val archiveFile: Provider<RegularFile>,
)

/** Registers the private probe once so all distribution surfaces consume the identical archive. */
internal fun Project.registerNativeSqliteFormatBoundaryProbe(
    javaCompilerExecutable: Provider<RegularFile>,
    javaVersion: Int,
): NativeSqliteFormatBoundaryProbe {
    val sourceFile =
        rootProject.layout.projectDirectory.file(
            "scripts/release_smoke_workflow/field_matrix/NativeSqliteFormatBoundaryProbe.java",
        )
    val classesDirectory =
        layout.buildDirectory.dir(
            "generated/release-smoke/native-sqlite-format-boundary-probe/classes",
        )
    val compileTask =
        tasks.register<CompileNativeSqliteFormatBoundaryProbeTask>(
            "compileNativeSqliteFormatBoundaryProbe",
        ) {
            group = "distribution"
            description = "Compiles the private unnamed-module SQLite format-boundary release-smoke probe."
            this.sourceFile.set(sourceFile)
            javaCompiler.set(javaCompilerExecutable)
            this.javaVersion.set(javaVersion)
            outputDirectory.set(classesDirectory)
        }
    val packageTask =
        tasks.register<Zip>("packageNativeSqliteFormatBoundaryProbe") {
            group = "distribution"
            description = "Packages the private archive-native SQLite format-boundary release-smoke probe."
            dependsOn(compileTask)
            archiveFileName.set("native-sqlite-format-boundary-probe.jar")
            destinationDirectory.set(layout.buildDirectory.dir("generated/release-smoke"))
            isReproducibleFileOrder = true
            isPreserveFileTimestamps = false
            from(classesDirectory)
        }
    return NativeSqliteFormatBoundaryProbe(
        packageTask = packageTask,
        archiveFile = packageTask.flatMap { it.archiveFile },
    )
}
