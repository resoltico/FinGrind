package dev.erst.fingrind.buildlogic

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

private val wildcardImportPattern = Regex("""^import\s+(static\s+)?[\w.]+\.\*;$""")
private const val JACKSON_DATABIND_GROUP = "tools.jackson.core"
private const val JACKSON_DATABIND_MODULE = "jackson-databind"
private const val LEGACY_JACKSON_GROUP = "com.fasterxml.jackson.core"

internal fun Project.registerJavaSourcePolicyTask() =
    tasks.register<VerifyJavaSourcePoliciesTask>("verifyJavaSourcePolicies") {
        group = "verification"
        description = "Fails the build when Java source files use forbidden wildcard imports."
        sourceFiles.from(
            fileTree(projectDir) {
                include("src/*/java/**/*.java")
                exclude("**/build/**", "**/.gradle/**")
            },
        )
    }

internal fun Project.registerJacksonDependencyPolicyTask() =
    tasks.register<VerifyJacksonDependencyPolicyTask>("verifyJacksonDependencyPolicy") {
        group = "verification"
        description =
            "Fails the build when projects declare direct Jackson dependencies outside the approved databind entrypoint or require the legacy Jackson annotation module."
        directDependencies.set(
            configurations
                .sortedBy { it.name }
                .flatMap { configuration ->
                    configuration.dependencies
                        .withType(ExternalModuleDependency::class.java)
                        .sortedWith(compareBy({ it.group }, { it.name }))
                        .map { dependency ->
                            "${configuration.name}|${dependency.group.orEmpty()}|${dependency.name}"
                        }
                },
        )
        policyFiles.from(
            layout.projectDirectory.file("src/main/java/module-info.java"),
            rootProject.file("gradle/libs.versions.toml"),
            layout.projectDirectory.file("../gradle/libs.versions.toml"),
        )
    }

abstract class VerifyJavaSourcePoliciesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val violations = mutableListOf<String>()
        sourceFiles.files
            .sortedBy { it.invariantSeparatorsPath() }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        if (wildcardImportPattern.matches(line.trim())) {
                            violations +=
                                "${file.displayPath(project.projectDir)}:${index + 1}: wildcard imports are forbidden."
                        }
                    }
                }
            }
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("FinGrind source-policy violations:")
                    violations.forEach(::appendLine)
                },
            )
        }
    }
}

abstract class VerifyJacksonDependencyPolicyTask : DefaultTask() {
    @get:Input
    abstract val directDependencies: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val policyFiles: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val violations = mutableListOf<String>()
        directDependencies.get().forEach { dependencyCoordinate ->
            val (configuration, group, module) = dependencyCoordinate.split('|', limit = 3)
            when (group) {
                LEGACY_JACKSON_GROUP ->
                    violations +=
                        "${project.path}:$configuration declares forbidden direct dependency $group:$module."

                JACKSON_DATABIND_GROUP ->
                    if (module != JACKSON_DATABIND_MODULE) {
                        violations +=
                            "${project.path}:$configuration declares unsupported direct Jackson module $group:$module; declare only $JACKSON_DATABIND_GROUP:$JACKSON_DATABIND_MODULE directly."
                    }
            }
        }
        policyFiles.files
            .filter(File::exists)
            .sortedBy { it.invariantSeparatorsPath() }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        if (legacyJacksonModuleRequirePattern.matches(line.trim())) {
                            violations +=
                                "${file.displayPath(project.projectDir)}:${index + 1}: module-info must not require com.fasterxml.jackson.annotation directly; require only tools.jackson.databind."
                        }
                        if (forbiddenJacksonCatalogPattern.containsMatchIn(line)) {
                            violations +=
                                "${file.displayPath(project.projectDir)}:${index + 1}: repository-owned Jackson catalog entries must not declare jackson-annotations or direct com.fasterxml.jackson.core coordinates."
                        }
                    }
                }
            }
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("FinGrind Jackson dependency-policy violations:")
                    violations.forEach(::appendLine)
                },
            )
        }
    }
}

private fun File.invariantSeparatorsPath(): String = path.replace(File.separatorChar, '/')
private fun File.displayPath(projectDir: File): String =
    runCatching { relativeTo(projectDir).invariantSeparatorsPath() }.getOrElse { invariantSeparatorsPath() }

private val legacyJacksonModuleRequirePattern =
    Regex("""^requires(?:\s+static|\s+transitive)?\s+com\.fasterxml\.jackson\.annotation\s*;$""")
private val forbiddenJacksonCatalogPattern =
    Regex("""\bjackson-annotations\s*=|com\.fasterxml\.jackson\.core:""")
