package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.gradle.api.tasks.testing.Test as GradleTest
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.junit.jupiter.api.io.TempDir

class FinGrindJavaCoverageConventionsTest {
    @TempDir lateinit var temporaryDirectory: Path

    @Test
    fun testTask_declaresItsJacocoExecutionDataAsAnOutput() {
        val project =
            ProjectBuilder.builder()
                .withProjectDir(temporaryDirectory.resolve("coverage-conventions").toFile())
                .build()
        project.pluginManager.apply("java")
        project.pluginManager.apply("jacoco")

        project.configureJavaCoverageConventions()

        val testTask = project.tasks.named("test", GradleTest::class.java).get()
        val destinationFile =
            testTask.extensions.getByType(JacocoTaskExtension::class.java).destinationFile

        assertTrue(destinationFile in testTask.outputs.files.files)
    }

    @Test
    fun testTask_usesAnExplicitPrivateRuntimeRootWhenConfigured() {
        val privateRuntimeRoot = temporaryDirectory.resolve("private-test-runtime-root")
        Files.createDirectories(privateRuntimeRoot)

        val configuredDirectories =
            selectTestRuntimeDirectories(
                configuredPrivateRoot = privateRuntimeRoot.toFile(),
                defaultTemporaryDirectory = temporaryDirectory.toFile(),
            )
        assertEquals(privateRuntimeRoot.toFile(), configuredDirectories.temporaryDirectory)
        assertEquals(privateRuntimeRoot.toFile(), configuredDirectories.privateRoot)
        assertEquals(
            mapOf(
                "java.io.tmpdir" to privateRuntimeRoot.toFile().absolutePath,
                "user.home" to privateRuntimeRoot.toFile().absolutePath,
            ),
            configuredDirectories.systemProperties,
        )

        val defaultDirectories =
            selectTestRuntimeDirectories(
                configuredPrivateRoot = null,
                defaultTemporaryDirectory = temporaryDirectory.toFile(),
            )
        assertEquals(
            temporaryDirectory.toFile(),
            defaultDirectories.temporaryDirectory,
        )
        assertEquals(null, defaultDirectories.privateRoot)
        assertEquals(
            mapOf("java.io.tmpdir" to temporaryDirectory.toFile().absolutePath),
            defaultDirectories.systemProperties,
        )
    }

    @Test
    fun coverageInvocationRequiresFreshTestEvidenceOnlyForCoverageTasks() {
        assertTrue(
            JavaCoverageExecutionInputs.requiresFreshTestExecution(
                listOf(":core:jacocoTestCoverageVerification"),
            ),
        )
        assertTrue(
            JavaCoverageExecutionInputs.requiresFreshTestExecution(listOf("coverage")),
        )
        assertTrue(
            !JavaCoverageExecutionInputs.requiresFreshTestExecution(listOf("test", "check")),
        )
    }

    @Test
    fun coverageInputsAndReportDependenciesRemainLiveForLaterRegisteredTestTasks() {
        val project = coverageProject("coverage-live-inputs")

        project.configureJavaCoverageConventions()

        val testTasks = project.tasks.withType(GradleTest::class.java)
        val expectedTestTaskPaths =
            JavaCoverageExecutionInputs.testTaskPaths(project.providers, testTasks)
        val jacocoExecutionData =
            JavaCoverageExecutionInputs.jacocoExecutionData(project.providers, testTasks)
        val laterTestTask = project.tasks.register("laterTest", GradleTest::class.java).get()
        val laterDestinationFile =
            laterTestTask.extensions.getByType(JacocoTaskExtension::class.java).destinationFile
        val jacocoReport =
            project.tasks.named("jacocoTestReport", JacocoReport::class.java).get()

        assertEquals(setOf(":laterTest", ":test"), expectedTestTaskPaths.get())
        assertTrue(laterDestinationFile in jacocoExecutionData.get())
        assertTrue(laterDestinationFile in jacocoReport.executionData.files)
        assertTrue(laterTestTask in jacocoReport.taskDependencies.getDependencies(jacocoReport))
    }

    @Test
    fun coverageClassDirectories_follow_late_build_directory_relocation() {
        val project = coverageProject("coverage-relocated-build-directory")

        project.configureJavaCoverageConventions()
        val relocatedBuildDirectory = project.layout.projectDirectory.dir("relocated-build")
        project.layout.buildDirectory.set(relocatedBuildDirectory)
        val relocatedClassFile =
            relocatedBuildDirectory.file("classes/java/main/example/CoverageEvidence.class").asFile
        Files.createDirectories(relocatedClassFile.parentFile.toPath())
        Files.write(relocatedClassFile.toPath(), byteArrayOf())

        val jacocoReport =
            project.tasks.named("jacocoTestReport", JacocoReport::class.java).get()

        assertTrue(
            relocatedClassFile in jacocoReport.classDirectories.files,
        )
    }

    @Test
    fun coverageAdmission_rejectsBuildDefinedTestSelection() {
        val testTask = testTask()
        testTask.filter.includeTestsMatching("example.SelectedTest")

        val failure =
            assertFailsWith<IllegalStateException> {
                JavaCoverageExecutionAdmission.requireCompleteFreshTestRun(
                    reportTaskPath = ":jacocoTestReport",
                    expectedTestTaskPaths = setOf(testTask.path),
                    executedTestTaskPaths = setOf(testTask.path),
                    testTaskSelectionRestrictions =
                        mapOf(
                            testTask.path to
                                JavaCoverageExecutionAdmission.testSelectionRestrictions(testTask),
                        ),
                )
            }

        assertEquals(
            "JaCoCo coverage report rejects filtered Test task(s): :test: " +
                "test include [example.SelectedTest]. " +
                "Run coverage without --tests or Test include/exclude filters.",
            failure.message,
        )
    }

    @Test
    fun coverageAdmission_rejectsCommandLineTestSelection() {
        val testTask = testTask()
        testTask.filter.javaClass
            .getMethod("setCommandLineIncludePatterns", Collection::class.java)
            .invoke(testTask.filter, setOf("example.SelectedTest"))

        val failure =
            assertFailsWith<IllegalStateException> {
                JavaCoverageExecutionAdmission.requireCompleteFreshTestRun(
                    reportTaskPath = ":jacocoTestReport",
                    expectedTestTaskPaths = setOf(testTask.path),
                    executedTestTaskPaths = setOf(testTask.path),
                    testTaskSelectionRestrictions =
                        mapOf(
                            testTask.path to
                                JavaCoverageExecutionAdmission.testSelectionRestrictions(testTask),
                        ),
                )
            }

        assertEquals(
            "JaCoCo coverage report rejects filtered Test task(s): :test: " +
                "command-line test include [example.SelectedTest]. " +
                "Run coverage without --tests or Test include/exclude filters.",
            failure.message,
        )
    }

    @Test
    fun coverageAdmission_rejectsTestTaskThatDidNotRunInThisInvocation() {
        val testTask = testTask()

        val failure =
            assertFailsWith<IllegalStateException> {
                JavaCoverageExecutionAdmission.requireCompleteFreshTestRun(
                    reportTaskPath = ":jacocoTestReport",
                    expectedTestTaskPaths = setOf(testTask.path),
                    executedTestTaskPaths = emptySet(),
                    testTaskSelectionRestrictions = emptyMap(),
                )
            }

        assertEquals(
            "JaCoCo coverage report :jacocoTestReport cannot use stale or incomplete execution data: " +
                "these required Test task(s) did not run in this Gradle invocation: :test. " +
                "Run coverage without excluding test tasks and with fresh test execution.",
            failure.message,
        )
    }

    @Test
    fun rootCoverageAdmission_rejectsOmittedProductionJavaProject() {
        val failure =
            assertFailsWith<IllegalStateException> {
                JavaCoverageExecutionAdmission.requireEveryDirectProductionJavaProjectAggregated(
                    directProductionJavaProjectPaths = setOf(":core", ":sqlite"),
                    aggregatedProductionJavaProjectPaths = setOf(":core"),
                )
            }

        assertEquals(
            "Root JaCoCo aggregation omits direct production Java project(s): :sqlite. " +
                "Every direct production Java project must apply dev.erst.fingrind.java-conventions.",
            failure.message,
        )
    }

    private fun testTask(): GradleTest {
        val project =
            ProjectBuilder.builder()
                .withProjectDir(temporaryDirectory.resolve("coverage-admission").toFile())
                .build()
        project.pluginManager.apply("java")
        return project.tasks.named("test", GradleTest::class.java).get()
    }

    private fun coverageProject(directoryName: String) =
        temporaryDirectory.resolve(directoryName).also { projectDirectory ->
            val sourceDirectory = projectDirectory.resolve("src/main/java/example")
            Files.createDirectories(sourceDirectory)
            Files.writeString(
                sourceDirectory.resolve("CoverageEvidence.java"),
                "package example; final class CoverageEvidence {}",
            )
        }.let { projectDirectory ->
            ProjectBuilder.builder()
                .withProjectDir(projectDirectory.toFile())
                .build()
                .also { project ->
                    project.pluginManager.apply("java")
                    project.pluginManager.apply("jacoco")
                }
        }

}
