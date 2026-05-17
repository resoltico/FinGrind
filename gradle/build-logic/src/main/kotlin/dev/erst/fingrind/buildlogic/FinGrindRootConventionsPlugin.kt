package dev.erst.fingrind.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.spotless.LineEnding
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileTree
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
                                "*.toml",
                                "**/*.gradle.kts",
                                "**/*.md",
                                "requirements*.txt",
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

            val pythonScripts = pythonScripts()
            val ruffConfig = layout.projectDirectory.file("ruff.toml")
            val requirementsFilePath = layout.projectDirectory.file("requirements-python-tools.txt")
            val pythonExecutableProvider =
                providers
                    .gradleProperty("fingrindPythonExecutable")
                    .orElse(defaultPythonExecutable())
            val ruffInstallHint =
                "Install the repo-owned Python tools with `${pythonExecutableProvider.get()} -m pip install --user -r ${requirementsFilePath.asFile.name}`."

            fun registerRuffTask(
                name: String,
                taskGroup: String,
                taskDescription: String,
                arguments: List<String>,
            ) = tasks.register<RuffTask>(name) {
                group = taskGroup
                description = taskDescription
                pythonExecutable.set(pythonExecutableProvider)
                ruffArguments.set(arguments)
                targetPaths.set(listOf("scripts"))
                installHint.set(ruffInstallHint)
                configFile.set(ruffConfig)
                requirementsFile.set(requirementsFilePath)
                sourceFiles.from(pythonScripts)
                workingDirectory.set(layout.projectDirectory)
                pythonPycacheDirectory.set(layout.buildDirectory.dir("tmp/python-pycache/$name"))
                ruffCacheDirectory.set(layout.buildDirectory.dir("tmp/ruff-cache/$name"))
            }

            val ruffCheck =
                registerRuffTask(
                    name = "ruffCheck",
                    taskGroup = "verification",
                    taskDescription = "Lint Python helper scripts with Ruff.",
                    arguments = listOf("check"),
                )
            val ruffFormatCheck =
                registerRuffTask(
                    name = "ruffFormatCheck",
                    taskGroup = "verification",
                    taskDescription = "Check Python helper script formatting with Ruff.",
                    arguments = listOf("format", "--check"),
                )
            registerRuffTask(
                name = "ruffFix",
                taskGroup = "formatting",
                taskDescription = "Apply safe Ruff lint fixes to Python helper scripts.",
                arguments = listOf("check", "--fix"),
            )
            registerRuffTask(
                name = "ruffFormat",
                taskGroup = "formatting",
                taskDescription = "Format Python helper scripts with Ruff.",
                arguments = listOf("format"),
            )
            val ruff =
                tasks.register("ruff") {
                    group = "verification"
                    description = "Runs all Ruff verification checks."
                    dependsOn(ruffCheck)
                    dependsOn(ruffFormatCheck)
                }

            tasks.named("check") {
                dependsOn("spotlessCheck")
                dependsOn(ruff)
            }

            val repositoryRootDirectory = layout.projectDirectory.asFile.toPath()
            val managedSqlitePackageId =
                DistributionContractReader.requiredSqliteSourcePackageId(repositoryRootDirectory)

            val managedSqlite =
                ManagedSqliteProvisioningLogic.register(
                    project = this,
                    repositoryRootDirectory = repositoryRootDirectory,
                    sqliteSourceDirectory = layout.projectDirectory.dir("third_party/sqlite/$managedSqlitePackageId"),
                    sqliteVersionValue =
                        DistributionContractReader.requiredMinimumSqliteVersion(repositoryRootDirectory),
                    sqlite3mcVersionValue =
                        DistributionContractReader.requiredSqlite3mcVersion(repositoryRootDirectory),
                    sourcePackageId = managedSqlitePackageId,
                )

            subprojects.forEach { subproject ->
                subproject.pluginManager.withPlugin("java-base") {
                    if (subproject.requiresManagedSqliteRuntime()) {
                        ManagedSqliteProvisioningLogic.configureConsumers(subproject, managedSqlite)
                    }
                }
            }

            val aggregatedCoverageReport = tasks.register<JacocoReport>("jacocoAggregatedReport") {
                group = "verification"
                description = "Aggregates JaCoCo coverage reports from all modules into a single report."

                reports {
                    xml.required.set(true)
                    xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/aggregated/report.xml"))
                    html.required.set(true)
                    html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/aggregated/html"))
                }
            }

            val coverage = tasks.register("coverage") {
                group = "verification"
                description =
                    "Runs tests, enforces coverage thresholds, and generates per-module and aggregated coverage reports."
                dependsOn(aggregatedCoverageReport)
            }

            subprojects.forEach { subproject ->
                subproject.pluginManager.withPlugin("java-base") {
                    val testTasks = subproject.tasks.withType(Test::class.java)
                    aggregatedCoverageReport.configure {
                        dependsOn(testTasks)
                        executionData.from(
                            subproject.provider {
                                testTasks.mapNotNull { testTask ->
                                    testTask.extensions.findByType(JacocoTaskExtension::class.java)?.destinationFile
                                }
                            },
                        )
                        sourceDirectories.from(subproject.layout.projectDirectory.dir("src/main/java"))
                        classDirectories.from(subproject.layout.buildDirectory.dir("classes/java/main"))
                    }
                    coverage.configure {
                        dependsOn("${subproject.path}:jacocoTestCoverageVerification")
                        dependsOn("${subproject.path}:jacocoTestReport")
                    }
                }
            }
        }
    }

    private fun Project.pythonScripts(): FileTree =
        fileTree(layout.projectDirectory.dir("scripts")) {
            include("**/*.py")
            exclude("**/__pycache__/**")
        }

    private fun defaultPythonExecutable(): String =
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            "python"
        } else {
            "python3"
        }

    private fun Project.requiresManagedSqliteRuntime(): Boolean =
        path == ":sqlite" || path == ":cli"
}
