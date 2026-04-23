package dev.erst.fingrind.buildlogic

import java.io.File
import org.gradle.api.Project

internal object FinGrindFilesystemLayout {
    private const val jacocoRootProperty = "fingrind.gradle.jacoco-root"
    private const val projectBuildRootProperty = "fingrind.gradle.project-build-root"

    fun jacocoDirectory(project: Project): File =
        project.providers.systemProperty(jacocoRootProperty)
            .orNull
            ?.takeIf(String::isNotBlank)
            ?.let { root -> File(root, projectSegment(project)) }
            ?: project.layout.buildDirectory.dir("jacoco").get().asFile

    fun jacocoDestinationFile(project: Project, testTaskName: String): File =
        jacocoDirectory(project).resolve("$testTaskName.exec")

    fun projectBuildDirectory(project: Project): File? =
        project.providers.systemProperty(projectBuildRootProperty).orNull
            ?.takeIf(String::isNotBlank)
            ?.let { buildRoot -> project.file("$buildRoot/${projectSegment(project)}") }
            ?: if (project.gradle.parent == null) {
                null
            } else {
                project.gradle.startParameter.projectCacheDir?.resolve("included-build/${projectSegment(project)}")
            }

    fun projectSegment(project: Project): String =
        if (project == project.rootProject) {
            "root"
        } else {
            project.path.removePrefix(":").replace(':', '/')
        }
}
