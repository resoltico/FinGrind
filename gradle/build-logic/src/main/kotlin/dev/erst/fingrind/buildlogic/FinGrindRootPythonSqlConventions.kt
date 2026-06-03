package dev.erst.fingrind.buildlogic

import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFile
import org.gradle.kotlin.dsl.register

internal fun Project.configureRootPythonAndSqlVerification(buildMetadata: FinGrindBuildMetadata.Values) {
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
    val uvBootstrapPythonProvider = defaultUvBootstrapPythonExecutable()
    val pythonToolsBootstrapHint =
        "Install the pinned uv launcher with `${uvBootstrapPythonProvider} -m pip install --user uv==${buildMetadata.uvVersion}`."
    val configuredPythonExecutableProvider =
        providers.gradleProperty("fingrindPythonExecutable")
    val uvExecutableProvider =
        providers
            .gradleProperty("fingrindUvExecutable")
            .orElse(defaultUvExecutable())

    fun registerUvToolTask(
        name: String,
        taskGroup: String,
        taskDescription: String,
        toolCommand: String,
        arguments: List<String>,
        targetPaths: List<String>,
        sourceFiles: FileTree,
        configFilePath: RegularFile? = null,
        toolCacheEnvironmentVariableName: String? = null,
    ) = tasks.register<UvToolTask>(name) {
        group = taskGroup
        description = taskDescription
        configuredPythonExecutableProvider.orNull?.let(configuredPythonExecutable::set)
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
}

private fun Project.pythonScripts(): FileTree =
    fileTree(layout.projectDirectory.dir("scripts")) {
        include("**/*.py")
        exclude("**/__pycache__/**")
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
