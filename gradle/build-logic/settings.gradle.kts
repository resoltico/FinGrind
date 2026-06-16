rootProject.name = "fingrind-build-logic"

pluginManagement {
    val buildMetadata =
        java.util.Properties().apply {
            file("../fingrind-build.properties").inputStream().use(::load)
        }
    val fingrindKotlinVersion =
        buildMetadata.getProperty("fingrindKotlinVersion")
            ?: error("Missing fingrindKotlinVersion in ../fingrind-build.properties.")
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "org.jetbrains.kotlin.jvm",
                "org.jetbrains.kotlin.plugin.sam.with.receiver",
                -> useVersion(fingrindKotlinVersion)
            }
        }
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../libs.versions.toml"))
        }
    }
}
