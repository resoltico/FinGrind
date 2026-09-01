package dev.erst.fingrind.buildlogic

import java.util.Properties
import org.gradle.api.Project

/** Loads the reviewed, source-controlled production mutation scope for one product module. */
internal object MutationScopeCatalog {
    fun configure(project: Project, scope: FinGrindMutationScopeExtension) {
        val module = project.path.removePrefix(":")
        val properties = Properties()
        val catalog = project.rootProject.file("gradle/mutation-scope.properties")
        catalog.inputStream().use(properties::load)
        require(module in configuredModules(properties)) {
            "No production mutation scope is defined for ${project.path}."
        }
        scope.targetClasses.set(values(properties, module, "targetClass").toSet())
        scope.targetTests.set(values(properties, module, "targetTest").toSet())
        scope.expectedMutationCounts.putAll(expectedMutationCounts(properties, module))
        scope.excludedProductionClasses.putAll(exclusions(properties, module))
    }

    private fun configuredModules(properties: Properties): Set<String> =
        properties.stringPropertyNames()
            .asSequence()
            .mapNotNull { key -> key.substringBefore('.', missingDelimiterValue = "").ifBlank { null } }
            .filter { module -> properties.stringPropertyNames().any { it.startsWith("$module.targetClass.") } }
            .toSet()

    private fun values(properties: Properties, module: String, name: String): List<String> {
        val prefix = "$module.$name."
        return properties.stringPropertyNames()
            .filter { key -> key.startsWith(prefix) }
            .sortedBy { key -> key.removePrefix(prefix).toInt() }
            .map { key -> properties.required(key) }
            .also { values -> require(values.isNotEmpty()) { "No $name scope configured for $module." } }
    }

    private fun exclusions(properties: Properties, module: String): Map<String, String> {
        val prefix = "$module.exclusion."
        return properties.stringPropertyNames()
            .filter { key -> key.startsWith(prefix) }
            .associate { key -> key.removePrefix(prefix) to properties.required(key) }
    }

    private fun expectedMutationCounts(properties: Properties, module: String): Map<String, Int> =
        values(properties, module, "expectedMutation")
            .associate { encoded ->
                val separator = encoded.lastIndexOf('|')
                require(separator > 0 && separator < encoded.lastIndex) {
                    "Invalid $module.expectedMutation entry: $encoded."
                }
                val className = encoded.substring(0, separator)
                val mutationCount = encoded.substring(separator + 1).toInt()
                require(mutationCount > 0) {
                    "Expected mutation count must be positive for $className."
                }
                className to mutationCount
            }

    private fun Properties.required(key: String): String =
        getProperty(key)?.takeIf(String::isNotBlank) ?: error("Missing $key in mutation scope catalog.")
}
