package dev.erst.fingrind.buildlogic

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

internal fun Project.configureJavaCoverageConventions() {
    tasks.withType<Test>().configureEach {
        modularity.inferModulePath.set(true)
        useJUnitPlatform()
        val jacocoDestinationFile =
            FinGrindFilesystemLayout.jacocoDestinationFile(project, name)
        extensions.configure(JacocoTaskExtension::class.java) {
            destinationFile = jacocoDestinationFile
        }
        doFirst {
            jacocoDestinationFile.parentFile.mkdirs()
            if (jacocoDestinationFile.exists() && !jacocoDestinationFile.delete()) {
                throw IllegalStateException(
                    "Unable to reset stale JaCoCo execution data at ${jacocoDestinationFile.absolutePath}.",
                )
            }
        }

        val progressPulseEnabled =
            providers.environmentVariable("FINGRIND_TEST_PULSE").map { it == "1" }.orElse(false).get()
        if (progressPulseEnabled) {
            val progressPulseIntervalMillis =
                providers.environmentVariable("FINGRIND_TEST_PULSE_INTERVAL_MS")
                    .map(String::toLong)
                    .orElse(15_000L)
                    .get()
            val pulseTaskPath = path
            val pulseProjectPath = project.path
            doFirst {
                addTestListener(
                    GradleTestPulseListener(
                        logger = logger,
                        taskPath = pulseTaskPath,
                        projectPath = pulseProjectPath,
                        pulseIntervalMillis = progressPulseIntervalMillis,
                    ),
                )
            }
        }
    }

    tasks.withType<JavaExec>().configureEach {
        modularity.inferModulePath.set(true)
    }

    val testTasks = tasks.withType<Test>()
    val jacocoExecutionData =
        providers.provider<List<java.io.File>> {
            testTasks.mapNotNull { testTask ->
                testTask.extensions.findByType(JacocoTaskExtension::class.java)?.destinationFile
            }
        }

    val mainSourceSet =
        extensions.findByType(JavaPluginExtension::class.java)?.sourceSets?.findByName("main")
            ?: throw IllegalStateException(
                "FinGrind Java conventions require a Java main source set for JaCoCo configuration.",
            )
    val jacocoXmlReport = layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml")
    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(testTasks)
        executionData.from(jacocoExecutionData)
        classDirectories.setFrom(mainSourceSet.output.classesDirs)
        sourceDirectories.setFrom(mainSourceSet.allJava.srcDirs)
        reports {
            xml.required.set(true)
            xml.outputLocation.set(jacocoXmlReport)
            html.required.set(true)
        }
    }

    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn("jacocoTestReport")
        executionData.from(jacocoExecutionData)
        classDirectories.setFrom(mainSourceSet.output.classesDirs)
        sourceDirectories.setFrom(mainSourceSet.allJava.srcDirs)
        doLast {
            JacocoXmlCoverageVerifier.verifyReport(jacocoXmlReport.get().asFile)
        }
    }
}
