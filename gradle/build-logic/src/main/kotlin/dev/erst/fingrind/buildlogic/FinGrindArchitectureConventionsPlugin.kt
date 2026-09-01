package dev.erst.fingrind.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/** Wires architecture verification to every registered production Java project. */
class FinGrindArchitectureConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        require(project.path == ":architecture") {
            "dev.erst.fingrind.architecture-conventions applies only to :architecture."
        }
        val productionProjects =
            project.rootProject.subprojects.filter { candidate ->
                candidate != project && candidate.projectDir.resolve("src/main/java").isDirectory
            }
        val libs = project.versionCatalog()
        project.dependencies.add("testImplementation", libs.library("archunit-junit6"))
        productionProjects.forEach { productionProject ->
            project.dependencies.add(
                "testImplementation",
                project.dependencies.project(mapOf("path" to productionProject.path)),
            )
        }

        project.tasks.withType<Test>().configureEach {
            useJUnitPlatform {
                includeEngines("archunit", "junit-jupiter")
            }
        }

        val verifyInventory =
            project.tasks.register<VerifyArchitectureTestInventoryTask>(
                "verifyArchitectureTestInventory",
            ) {
                ruleSource.set(
                    project.layout.projectDirectory.file(
                        "src/test/java/dev/erst/architecture/FinGrindArchitectureTest.java",
                    ),
                )
                inventoryTestSource.set(
                    project.layout.projectDirectory.file(
                        "src/test/java/dev/erst/architecture/ArchitectureSeamCatalogTest.java",
                    ),
                )
                testResultsDirectory.set(project.layout.buildDirectory.dir("test-results/test"))
                dependsOn(project.tasks.named("test"))
            }
        project.tasks.named("test") {
            finalizedBy(verifyInventory)
        }
    }
}
