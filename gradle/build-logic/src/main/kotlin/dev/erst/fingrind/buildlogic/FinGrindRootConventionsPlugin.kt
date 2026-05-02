package dev.erst.fingrind.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.spotless.LineEnding
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

class FinGrindRootConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            FinGrindFilesystemLayout.projectBuildDirectory(this)?.let { projectBuildDirectory ->
                layout.buildDirectory.set(projectBuildDirectory)
            }

            pluginManager.apply("base")
            pluginManager.apply("jacoco")
            pluginManager.apply("com.diffplug.spotless")

            val libs = versionCatalog()

            description = providers.gradleProperty("fingrindDescription").get()
            configureFinGrindArtifactRepositories()

            allprojects {
                group = providers.gradleProperty("group").get()
                version = providers.gradleProperty("version").get()
            }

            configure<JacocoPluginExtension> {
                toolVersion = libs.findVersion("jacoco").get().requiredVersion
            }

            configure<SpotlessExtension> {
                lineEndings = LineEnding.UNIX
                format("projectFiles") {
                    target(
                        fileTree(projectDir) {
                            include(
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
                            exclude(
                                "**/build/**",
                                "**/.claude/**",
                                "**/.gradle/**",
                                "**/.local/**",
                                "tmp/**",
                            )
                        }
                    )
                    trimTrailingWhitespace()
                    endWithNewline()
                }
            }

            tasks.named("check") {
                dependsOn("spotlessCheck")
            }

            val managedSqlitePackageId = requiredGradleProperty("fingrindManagedSqlitePackageId")
            val managedSqliteSourceSha3 = requiredGradleProperty("fingrindManagedSqliteSourceSha3")
            val repositoryRootDirectory = layout.projectDirectory.asFile.toPath()

            val managedSqlite =
                ManagedSqliteProvisioningLogic.register(
                    project = this,
                    repositoryRootDirectory = repositoryRootDirectory,
                    sourceDirectory = layout.projectDirectory.dir("third_party/sqlite/$managedSqlitePackageId"),
                    sqliteVersionValue =
                        DistributionContractReader.requiredMinimumSqliteVersion(repositoryRootDirectory),
                    sqlite3mcVersionValue =
                        DistributionContractReader.requiredSqlite3mcVersion(repositoryRootDirectory),
                    sourceSha3 = managedSqliteSourceSha3,
                )

            subprojects.forEach { subproject ->
                subproject.pluginManager.withPlugin("java-base") {
                    ManagedSqliteProvisioningLogic.configureConsumers(subproject, managedSqlite)
                }
            }

            val coverageProjects = subprojects.toList()
            val coverageTestTasks =
                coverageProjects.flatMap { subproject ->
                    subproject.tasks.withType(Test::class.java).toList()
                }
            val coverageExecutionData =
                providers.provider<List<java.io.File>> {
                    coverageTestTasks.mapNotNull { testTask ->
                        testTask.extensions.findByType(JacocoTaskExtension::class.java)?.destinationFile
                    }
                }

            tasks.register<JacocoReport>("jacocoAggregatedReport") {
                group = "verification"
                description = "Aggregates JaCoCo coverage reports from all modules into a single report."

                dependsOn(coverageTestTasks)

                executionData.from(coverageExecutionData)
                sourceDirectories.from(
                    coverageProjects.map { it.layout.projectDirectory.dir("src/main/java").asFile }
                )
                classDirectories.from(
                    coverageProjects.map {
                        it.layout.buildDirectory.dir("classes/java/main").get().asFile
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
