package dev.erst.fingrind.buildlogic

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

private const val javaCoverageExecutionLedgerServiceName = "fingrindJavaCoverageExecutionLedger"

internal fun Project.configureJavaCoverageConventions() {
    val coverageExecutionLedger =
        gradle.sharedServices.registerJavaCoverageExecutionLedger()
    val freshCoverageEvidenceRequired =
        JavaCoverageExecutionInputs.requiresFreshTestExecution(gradle.startParameter.taskNames)

    tasks.withType<Test>().configureEach {
        val testTaskPath = path
        usesService(coverageExecutionLedger)
        modularity.inferModulePath.set(true)
        useJUnitPlatform()
        val jacocoDestinationFile =
            FinGrindFilesystemLayout.jacocoDestinationFile(project, name)
        extensions.configure(JacocoTaskExtension::class.java) {
            destinationFile = jacocoDestinationFile
        }
        outputs.file(jacocoDestinationFile)
        if (freshCoverageEvidenceRequired) {
            outputs.upToDateWhen { false }
        }
        doFirst {
            val executingTestTask = this as Test
            coverageExecutionLedger.get().recordTestExecution(
                testTaskPath = testTaskPath,
                selectionRestrictions = JavaCoverageExecutionAdmission.testSelectionRestrictions(executingTestTask),
            )
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
    val expectedTestTaskPaths =
        JavaCoverageExecutionInputs.testTaskPaths(providers, testTasks)
    val jacocoExecutionData =
        JavaCoverageExecutionInputs.jacocoExecutionData(providers, testTasks)

    val mainSourceSet =
        extensions.findByType(JavaPluginExtension::class.java)?.sourceSets?.findByName("main")
            ?: throw IllegalStateException(
                "FinGrind Java conventions require a Java main source set for JaCoCo configuration.",
            )
    if (mainSourceSet.allJava.files.isEmpty()) {
        return
    }
    val coverageClassDirectories =
        files(
            providers.provider {
                mainSourceSet.output.classesDirs.files.map { classDirectory ->
                    fileTree(classDirectory)
                }
            },
        )
    val jacocoXmlReport = layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml")
    val jacocoReportTaskPath = pathOf("jacocoTestReport")
    tasks.named<JacocoReport>("jacocoTestReport") {
        usesService(coverageExecutionLedger)
        dependsOn(testTasks)
        executionData.from(jacocoExecutionData)
        classDirectories.setFrom(coverageClassDirectories)
        sourceDirectories.setFrom(mainSourceSet.allJava.srcDirs)
        reports {
            xml.required.set(true)
            xml.outputLocation.set(jacocoXmlReport)
            html.required.set(true)
        }
        doFirst {
            JavaCoverageExecutionAdmission.requireCompleteFreshTestRun(
                reportTaskPath = jacocoReportTaskPath,
                expectedTestTaskPaths = expectedTestTaskPaths.get(),
                executedTestTaskPaths = coverageExecutionLedger.get().executedTestTaskPaths(),
                testTaskSelectionRestrictions =
                    coverageExecutionLedger.get().testTaskSelectionRestrictions(),
            )
        }
    }

    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn("jacocoTestReport")
        executionData.from(jacocoExecutionData)
        classDirectories.setFrom(coverageClassDirectories)
        sourceDirectories.setFrom(mainSourceSet.allJava.srcDirs)
        doLast {
            JacocoXmlCoverageVerifier.verifyReport(jacocoXmlReport.get().asFile)
        }
    }
}

private fun Project.pathOf(taskName: String): String =
    if (path == ":") ":$taskName" else "$path:$taskName"

private fun BuildServiceRegistry.registerJavaCoverageExecutionLedger() =
    registerIfAbsent(
        javaCoverageExecutionLedgerServiceName,
        JavaCoverageExecutionLedger::class.java,
    ) {}
