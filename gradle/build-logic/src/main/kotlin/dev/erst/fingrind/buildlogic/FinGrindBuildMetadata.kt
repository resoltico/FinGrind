package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import org.gradle.api.Project

/** Canonical build metadata shared by root builds, nested builds, and publication automation. */
object FinGrindBuildMetadata {
    private const val BUILD_METADATA_PATH = "gradle/fingrind-build.properties"

    data class Values(
        val javaVersion: Int,
        val implementationVendor: String,
        val implementationLicense: String,
        val foojayResolverConventionVersion: String,
    )

    fun load(project: Project): Values = load(project.rootProject.projectDir.toPath())

    fun load(projectRootDirectory: Path): Values {
        val properties = Properties()
        Files.newInputStream(metadataPath(projectRootDirectory)).use(properties::load)
        return Values(
            javaVersion = property(properties, "fingrindJavaVersion").toInt(),
            implementationVendor = property(properties, "implementationVendor"),
            implementationLicense = property(properties, "implementationLicense"),
            foojayResolverConventionVersion = property(properties, "foojayResolverConventionVersion"),
        )
    }

    private fun metadataPath(projectRootDirectory: Path): Path =
        sequenceOf(
                projectRootDirectory.resolve(BUILD_METADATA_PATH),
                projectRootDirectory.resolve("..").resolve(BUILD_METADATA_PATH),
            )
            .firstOrNull(Files::isRegularFile)
            ?: throw IllegalArgumentException(
                "Missing canonical build metadata file $BUILD_METADATA_PATH for $projectRootDirectory.",
            )

    private fun property(properties: Properties, name: String): String =
        properties.getProperty(name)
            ?: throw IllegalArgumentException("Missing build metadata property '$name'.")
}
