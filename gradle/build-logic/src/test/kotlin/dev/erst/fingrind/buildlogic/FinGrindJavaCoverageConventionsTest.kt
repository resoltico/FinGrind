package dev.erst.fingrind.buildlogic

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import org.gradle.api.tasks.testing.Test as GradleTest
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
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
}
