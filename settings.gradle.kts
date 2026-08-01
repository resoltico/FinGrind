pluginManagement {
    includeBuild("gradle/build-logic")
    val buildMetadata =
        java.util.Properties().apply {
            file("gradle/fingrind-build.properties").inputStream().use { stream -> load(stream) }
        }
    val foojayResolverConventionVersion =
        buildMetadata.getProperty("foojayResolverConventionVersion")
            ?: error("Missing foojayResolverConventionVersion in gradle/fingrind-build.properties.")
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version foojayResolverConventionVersion
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

rootProject.name = "FinGrind"
include("core", "contract", "executor", "sqlite", "report-pdf", "cli", "architecture")
