package dev.erst.fingrind.buildlogic

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

private const val finGrindJavaConventionsPluginId = "dev.erst.fingrind.java-conventions"

internal fun Project.configureRootCoverageAggregation() {
    val directProductionJavaProjectPaths = linkedSetOf<String>()
    val aggregatedProductionJavaProjectPaths = linkedSetOf<String>()

    val coverageAdmission =
        tasks.register("verifyRootCoverageAdmission") {
            group = "verification"
            description =
                "Rejects a root coverage aggregate that omits a direct production Java project."
            doLast {
                JavaCoverageExecutionAdmission.requireEveryDirectProductionJavaProjectAggregated(
                    directProductionJavaProjectPaths = directProductionJavaProjectPaths,
                    aggregatedProductionJavaProjectPaths = aggregatedProductionJavaProjectPaths,
                )
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
        dependsOn(coverageAdmission)
    }

    val coverage =
        tasks.register("coverage") {
            group = "verification"
            description =
                "Runs tests, enforces coverage thresholds, and generates per-module and aggregated coverage reports."
            dependsOn(aggregatedCoverageReport)
        }

    subprojects.forEach { subproject ->
        subproject.pluginManager.withPlugin("java") {
            if (!subproject.hasProductionJavaSources()) {
                return@withPlugin
            }
            directProductionJavaProjectPaths.add(subproject.path)

            subproject.pluginManager.withPlugin(finGrindJavaConventionsPluginId) {
                aggregatedProductionJavaProjectPaths.add(subproject.path)
                val testTasks = subproject.tasks.withType(Test::class.java)
                val jacocoExecutionData =
                    JavaCoverageExecutionInputs.jacocoExecutionData(subproject.providers, testTasks)
                val sourceDirectory = subproject.layout.projectDirectory.dir("src/main/java")
                val classDirectory = subproject.layout.buildDirectory.dir("classes/java/main")
                val jacocoReport =
                    subproject.tasks.named("jacocoTestReport", JacocoReport::class.java)
                val coverageVerification =
                    subproject.tasks.named(
                        "jacocoTestCoverageVerification",
                        JacocoCoverageVerification::class.java,
                    )
                aggregatedCoverageReport.configure {
                    dependsOn(testTasks)
                    dependsOn(jacocoReport)
                    executionData.from(jacocoExecutionData)
                    sourceDirectories.from(sourceDirectory)
                    classDirectories.from(classDirectory)
                }
                coverage.configure {
                    dependsOn(coverageVerification)
                }
            }
        }
    }
}

private fun Project.hasProductionJavaSources(): Boolean {
    val mainSourceSet =
        extensions.findByType(JavaPluginExtension::class.java)?.sourceSets?.findByName("main")
            ?: return false
    return mainSourceSet.allJava.files.isNotEmpty()
}
