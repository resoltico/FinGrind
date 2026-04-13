package dev.erst.fingrind.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

class FinGrindRootConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            pluginManager.apply("base")
            pluginManager.apply("jacoco")
            pluginManager.apply("com.diffplug.spotless")

            val libs = versionCatalog()

            description = providers.gradleProperty("fingrindDescription").get()
            repositories.mavenCentral()

            allprojects {
                group = providers.gradleProperty("group").get()
                version = providers.gradleProperty("version").get()
            }

            configure<JacocoPluginExtension> {
                toolVersion = libs.findVersion("jacoco").get().requiredVersion
            }

            configure<SpotlessExtension> {
                format("projectFiles") {
                    target(
                        ".gitattributes",
                        ".gitignore",
                        ".dockerignore",
                        "Dockerfile",
                        "**/*.gradle.kts",
                        "**/*.md",
                        "**/*.yml",
                        "gradle.properties",
                        "gradle/**/*.toml",
                        "docs/**/*.json",
                        "examples/**/*.json",
                    )
                    targetExclude("**/build/**", "**/.gradle/**")
                    trimTrailingWhitespace()
                    endWithNewline()
                }
            }

            tasks.named("check") {
                dependsOn("spotlessCheck")
            }

            val managedSqliteVersion = requiredGradleProperty("fingrindManagedSqliteVersion")
            val managedSqliteAmalgamationId =
                requiredGradleProperty("fingrindManagedSqliteAmalgamationId")
            val managedSqliteSourceSha3 = requiredGradleProperty("fingrindManagedSqliteSourceSha3")

            val managedSqlite =
                ManagedSqliteSupport.register(
                    project = this,
                    sourceDirectory =
                        layout.projectDirectory.dir(
                            "third_party/sqlite/sqlite-amalgamation-$managedSqliteAmalgamationId",
                        ),
                    sqliteVersionValue = managedSqliteVersion,
                    sourceSha3 = managedSqliteSourceSha3,
                )

            subprojects.forEach { subproject ->
                subproject.pluginManager.withPlugin("java-base") {
                    ManagedSqliteSupport.configureConsumers(subproject, managedSqlite)
                }
            }

            val coverageProjects = subprojects.toList()

            tasks.register<JacocoReport>("jacocoAggregatedReport") {
                group = "verification"
                description = "Aggregates JaCoCo coverage reports from all modules into a single report."

                dependsOn(coverageProjects.map { "${it.path}:test" })

                executionData.from(
                    coverageProjects.map { subproject ->
                        subproject.fileTree("${subproject.projectDir}/build/jacoco") {
                            include("*.exec")
                        }
                    }
                )
                sourceDirectories.from(
                    coverageProjects.map { file("${it.projectDir}/src/main/java") }
                )
                classDirectories.from(
                    coverageProjects.map {
                        fileTree("${it.projectDir}/build/classes/java/main") {
                            exclude("**/module-info.class")
                        }
                    }
                )

                reports {
                    xml.required.set(true)
                    xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/aggregated/report.xml"))
                    html.required.set(true)
                    html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/aggregated/html"))
                }
            }

            tasks.register("coverage") {
                group = "verification"
                description =
                    "Runs tests, enforces coverage thresholds, and generates per-module and aggregated coverage reports."
                dependsOn(coverageProjects.map { "${it.path}:jacocoTestCoverageVerification" })
                dependsOn(coverageProjects.map { "${it.path}:jacocoTestReport" })
                dependsOn("jacocoAggregatedReport")
            }
        }
    }

    private fun Project.requiredGradleProperty(name: String): String =
        providers.gradleProperty(name).orNull
            ?: throw IllegalArgumentException("Missing Gradle property: $name")
}
