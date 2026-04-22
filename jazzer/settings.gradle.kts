pluginManagement {
    includeBuild("../gradle/build-logic")
    val foojayResolverConventionVersion =
        Regex("""(?m)^foojay-resolver-convention\s*=\s*"([^"]+)"\s*$""")
            .find(file("../gradle/libs.versions.toml").readText())
            ?.groupValues
            ?.get(1)
            ?: error("Missing foojay-resolver-convention version in ../gradle/libs.versions.toml.")
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
