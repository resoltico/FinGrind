package dev.erst.fingrind.buildlogic

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.GradleException
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.kotlin.dsl.named

internal fun Project.configureCliExecutionSurfaceConventions(
    cliContractBuildLogicInputs: FileCollection,
) {
    tasks.named<JavaExec>("run") {
        workingDir = rootProject.projectDir
        enableCliAndCoreNamedNativeAccess()
    }

    tasks.named<ProcessResources>("processResources") {
        dependsOn(rootProject.tasks.named("prepareManagedSqlite"))
        val descriptionText: String = providers.gradleProperty("fingrindDescription").get()
        val versionText: String = version.toString()
        inputs.property("fingrindDescription", descriptionText)
        inputs.property("fingrindVersion", versionText)
        filesMatching("fingrind.properties") {
            expand(
                mapOf(
                    "fingrindDescription" to descriptionText,
                    "version" to versionText,
                ),
            )
        }
    }

    disableLegacyCliDistributionTasks()

    tasks.named<Test>("test") {
        inputs.file(rootProject.layout.projectDirectory.file("Dockerfile"))
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.file(rootProject.layout.projectDirectory.file(".gitignore"))
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.file(rootProject.layout.projectDirectory.file(".gitattributes"))
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.file(rootProject.layout.projectDirectory.file("AGENTS.md"))
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.file(rootProject.layout.projectDirectory.file(".codex/UNIVERSAL_ENGINEERING_CONTRACT.md"))
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.file(layout.projectDirectory.file("build.gradle.kts"))
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.dir(rootProject.layout.projectDirectory.dir("docs"))
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.dir(rootProject.layout.projectDirectory.dir("scripts"))
            .withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(cliContractBuildLogicInputs)
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}

private fun Project.disableLegacyCliDistributionTasks() {
    tasks.named<CreateStartScripts>("startScripts") {
        description =
            "Unsupported legacy CLI distribution surface. Use :cli:bundleCliArchive or ./scripts/source-checkout-cli.sh instead."
        doFirst { throw legacyCliDistributionTaskFailure(name) }
    }

    tasks.named<Sync>("installDist") {
        description =
            "Unsupported legacy CLI distribution surface. Use :cli:bundleCliArchive or ./scripts/source-checkout-cli.sh instead."
        doFirst { throw legacyCliDistributionTaskFailure(name) }
    }

    tasks.named<Tar>("distTar") {
        description =
            "Unsupported legacy CLI distribution surface. Use :cli:bundleCliArchive or ./scripts/source-checkout-cli.sh instead."
        doFirst { throw legacyCliDistributionTaskFailure(name) }
    }

    tasks.named<Zip>("distZip") {
        description =
            "Unsupported legacy CLI distribution surface. Use :cli:bundleCliArchive or ./scripts/source-checkout-cli.sh instead."
        doFirst { throw legacyCliDistributionTaskFailure(name) }
    }

    tasks.matching {
        it.name == "startShadowScripts" ||
            it.name == "installShadowDist" ||
            it.name == "shadowDistTar" ||
            it.name == "shadowDistZip"
    }.configureEach {
        description =
            "Unsupported legacy CLI distribution surface. Use :cli:bundleCliArchive or ./scripts/source-checkout-cli.sh instead."
        doFirst { throw legacyCliDistributionTaskFailure(name) }
    }
}

private fun legacyCliDistributionTaskFailure(taskName: String): GradleException =
    GradleException(
        "$taskName is a removed legacy CLI distribution task. Use :cli:bundleCliArchive for the supported public bundle artifact, or run ./scripts/source-checkout-cli.sh for the source-checkout launcher surface.",
    )
