pluginManagement {
    includeBuild("../gradle/build-logic")
    val buildMetadata =
        java.util.Properties().apply {
            file("../gradle/fingrind-build.properties").inputStream().use { stream -> load(stream) }
        }
    val foojayResolverConventionVersion =
        buildMetadata.getProperty("foojayResolverConventionVersion")
            ?: error("Missing foojayResolverConventionVersion in ../gradle/fingrind-build.properties.")
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version foojayResolverConventionVersion
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

rootProject.name = "FinGrind-Jazzer"

includeBuild("..")
