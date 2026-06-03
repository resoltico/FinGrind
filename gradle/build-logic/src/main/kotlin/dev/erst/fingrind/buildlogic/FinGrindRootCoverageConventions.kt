package dev.erst.fingrind.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

internal fun Project.configureRootCoverageAggregation() {
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

    val coverage =
        tasks.register("coverage") {
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
