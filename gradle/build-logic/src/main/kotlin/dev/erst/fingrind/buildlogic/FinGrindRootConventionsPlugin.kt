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
            val buildMetadata = FinGrindBuildMetadata.load(this)

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
                                "gradle/sqlfluff/sqlfluff.cfg",
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
            val canonicalSqliteSchemaFile =
                layout.projectDirectory.file("sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql")
            val canonicalSqliteSchemaFiles =
                fileTree(layout.projectDirectory) {
                    include("sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql")
                }
            val ruffConfig = layout.projectDirectory.file("ruff.toml")
            val sqlfluffConfig = layout.projectDirectory.file("gradle/sqlfluff/sqlfluff.cfg")
            val requirementsFilePath = layout.projectDirectory.file("requirements-python-tools.txt")
            val pythonExecutableProvider =
                providers
                    .gradleProperty("fingrindPythonExecutable")
                    .orElse(defaultPythonExecutable(buildMetadata.pythonVersion))
            val uvExecutableProvider =
                providers
                    .gradleProperty("fingrindUvExecutable")
                    .orElse(defaultUvExecutable())
            val uvBootstrapPythonProvider = defaultUvBootstrapPythonExecutable()
            val pythonToolsBootstrapHint =
                "Install the pinned uv launcher with `${uvBootstrapPythonProvider} -m pip install --user uv==${buildMetadata.uvVersion}`."

            fun registerUvToolTask(
                name: String,
                taskGroup: String,
                taskDescription: String,
                toolCommand: String,
                arguments: List<String>,
                targetPaths: List<String>,
                sourceFiles: FileTree,
                configFilePath: org.gradle.api.file.RegularFile? = null,
                toolCacheEnvironmentVariableName: String? = null,
            ) = tasks.register<UvToolTask>(name) {
                group = taskGroup
                description = taskDescription
                pythonExecutable.set(pythonExecutableProvider)
                uvExecutable.set(uvExecutableProvider)
                requiredPythonVersion.set(buildMetadata.pythonVersion)
                requiredUvVersion.set(buildMetadata.uvVersion)
                this.toolCommand.set(toolCommand)
                toolArguments.set(arguments)
                this.targetPaths.set(targetPaths)
                bootstrapHint.set(pythonToolsBootstrapHint)
                if (configFilePath != null) {
                    configFile.set(configFilePath)
                }
                requirementsFile.set(requirementsFilePath)
                this.sourceFiles.from(sourceFiles)
                workingDirectory.set(layout.projectDirectory)
                uvCacheDirectory.set(layout.buildDirectory.dir("tmp/uv-cache/$name"))
                toolCacheDirectory.set(layout.buildDirectory.dir("tmp/python-tool-cache/$name"))
                if (toolCacheEnvironmentVariableName != null) {
                    toolCacheEnvironmentVariable.set(toolCacheEnvironmentVariableName)
                }
            }

            val ruffCheck =
                registerUvToolTask(
                    name = "ruffCheck",
                    taskGroup = "verification",
                    taskDescription = "Lint Python helper scripts with Ruff.",
                    toolCommand = "ruff",
                    arguments = listOf("check"),
                    targetPaths = listOf("scripts"),
                    sourceFiles = pythonScripts,
                    configFilePath = ruffConfig,
                    toolCacheEnvironmentVariableName = "RUFF_CACHE_DIR",
                )
            val ruffFormatCheck =
                registerUvToolTask(
                    name = "ruffFormatCheck",
                    taskGroup = "verification",
                    taskDescription = "Check Python helper script formatting with Ruff.",
                    toolCommand = "ruff",
                    arguments = listOf("format", "--check"),
                    targetPaths = listOf("scripts"),
                    sourceFiles = pythonScripts,
                    configFilePath = ruffConfig,
                    toolCacheEnvironmentVariableName = "RUFF_CACHE_DIR",
                )
            registerUvToolTask(
                name = "ruffFix",
                taskGroup = "formatting",
                taskDescription = "Apply safe Ruff lint fixes to Python helper scripts.",
                toolCommand = "ruff",
                arguments = listOf("check", "--fix"),
                targetPaths = listOf("scripts"),
                sourceFiles = pythonScripts,
                configFilePath = ruffConfig,
                toolCacheEnvironmentVariableName = "RUFF_CACHE_DIR",
            )
            registerUvToolTask(
                name = "ruffFormat",
                taskGroup = "formatting",
                taskDescription = "Format Python helper scripts with Ruff.",
                toolCommand = "ruff",
                arguments = listOf("format"),
                targetPaths = listOf("scripts"),
                sourceFiles = pythonScripts,
                configFilePath = ruffConfig,
                toolCacheEnvironmentVariableName = "RUFF_CACHE_DIR",
            )
            val ruff =
                tasks.register("ruff") {
                    group = "verification"
                    description = "Runs all Ruff verification checks."
                    dependsOn(ruffCheck)
                    dependsOn(ruffFormatCheck)
                }

            val sqlfluffCheck =
                registerUvToolTask(
                    name = "sqlfluffCheck",
                    taskGroup = "verification",
                    taskDescription = "Lint the canonical SQLite schema with SQLFluff.",
                    toolCommand = "sqlfluff",
                    arguments = listOf("lint"),
                    targetPaths = listOf(canonicalSqliteSchemaFile.asFile.invariantSeparatorsPath),
                    sourceFiles = canonicalSqliteSchemaFiles,
                    configFilePath = sqlfluffConfig,
                )
            registerUvToolTask(
                name = "sqlfluffFix",
                taskGroup = "formatting",
                taskDescription = "Apply SQLFluff fixes to the canonical SQLite schema.",
                toolCommand = "sqlfluff",
                arguments = listOf("fix", "--force"),
                targetPaths = listOf(canonicalSqliteSchemaFile.asFile.invariantSeparatorsPath),
                sourceFiles = canonicalSqliteSchemaFiles,
                configFilePath = sqlfluffConfig,
            )
            val sqlfluff =
                tasks.register("sqlfluff") {
                    group = "verification"
                    description = "Runs SQLFluff verification over the canonical SQLite schema."
                    dependsOn(sqlfluffCheck)
                }

            tasks.named("check") {
                dependsOn("spotlessCheck")
                dependsOn(ruff)
                dependsOn(sqlfluff)
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
                subproject.pluginManager.withPlugin("java") {
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

    private fun defaultPythonExecutable(requiredPythonVersion: String): String =
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            "python"
        } else {
            listOf("python3.13", "python3.12", "python3")
                .mapNotNull(::executableOnPath)
                .firstOrNull()
                ?: findUvManagedPythonExecutable(requiredPythonVersion)
                ?: "python3"
        }

    private fun defaultUvExecutable(): String =
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            "uv.exe"
        } else {
            "uv"
        }

    private fun defaultUvBootstrapPythonExecutable(): String =
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            "python"
        } else {
            "python3"
        }

    private fun executableOnPath(command: String): String? {
        val path = System.getenv("PATH") ?: return null
        val separator = System.getProperty("path.separator")
        return path.split(separator).firstNotNullOfOrNull { entry ->
            val candidate = java.nio.file.Path.of(entry, command)
            candidate.takeIf {
                java.nio.file.Files.isRegularFile(it) && java.nio.file.Files.isExecutable(it)
            }?.toAbsolutePath()?.toString()
        }
    }

    private fun findUvManagedPythonExecutable(requiredPythonVersion: String): String? =
        try {
            val process =
                ProcessBuilder(defaultUvExecutable(), "python", "find", requiredPythonVersion)
                    .redirectErrorStream(true)
                    .start()
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            if (process.waitFor() == 0 && output.isNotBlank()) {
                output.lineSequence().first().trim().takeIf(String::isNotBlank)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

    private fun Project.requiresManagedSqliteRuntime(): Boolean =
        path == ":sqlite" || path == ":cli"
}
