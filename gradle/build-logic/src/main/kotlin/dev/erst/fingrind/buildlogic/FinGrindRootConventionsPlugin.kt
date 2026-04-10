package dev.erst.fingrind.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class FinGrindRootConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
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
        }
    }

    private fun Project.requiredGradleProperty(name: String): String =
        providers.gradleProperty(name).orNull
            ?: throw IllegalArgumentException("Missing Gradle property: $name")
}
