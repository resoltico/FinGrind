package dev.erst.fingrind.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class FinGrindJavaConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            FinGrindFilesystemLayout.projectBuildDirectory(this)?.let { projectBuildDirectory ->
                layout.buildDirectory.set(projectBuildDirectory)
            }

            pluginManager.apply("java-base")
            pluginManager.apply("jacoco")
            pluginManager.apply("com.diffplug.spotless")
            pluginManager.apply("net.ltgt.errorprone")
            pluginManager.apply("pmd")

            configureFinGrindArtifactRepositories()

            val buildMetadata = FinGrindBuildMetadata.load(this)
            configurePinnedJacocoVersion()
            val fingrindJavaVersion = buildMetadata.javaVersion
            val implementationVendor = buildMetadata.implementationVendor
            val implementationLicense = buildMetadata.implementationLicense
            val enforcedErrorProneChecks =
                listOf(
                    "BadImport",
                    "BoxedPrimitiveConstructor",
                    "CheckReturnValue",
                    "EqualsIncompatibleType",
                    "JavaLangClash",
                    "MissingCasesInEnumSwitch",
                    "MissingOverride",
                    "NullAway",
                    "ReferenceEquality",
                    "RequireExplicitNullMarking",
                    "StringCaseLocaleUsage",
                )

            configureJavaRuntimeConventions(
                fingrindJavaVersion = fingrindJavaVersion,
                implementationVendor = implementationVendor,
                implementationLicense = implementationLicense,
                enforcedErrorProneChecks = enforcedErrorProneChecks,
            )
            configureJavaQualityConventions()
            configureJavaCoverageConventions()
        }
    }
}
