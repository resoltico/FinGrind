package dev.erst.fingrind.buildlogic

import java.io.File
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

/** Builds coverage evidence from the live Test-task collection at task realization time. */
internal object JavaCoverageExecutionInputs {
    /** Returns whether this invocation explicitly requests evidence that must come from a fresh test run. */
    fun requiresFreshTestExecution(requestedTaskPaths: List<String>): Boolean =
        requestedTaskPaths.any { requestedTaskPath ->
            requestedTaskPath.substringAfterLast(':') in freshCoverageTaskNames
        }

    fun testTaskPaths(
        providers: ProviderFactory,
        testTasks: TaskCollection<Test>,
    ): Provider<Set<String>> =
        providers.provider<Set<String>> {
            testTasks.map { testTask -> testTask.path }.toSortedSet()
        }

    fun jacocoExecutionData(
        providers: ProviderFactory,
        testTasks: TaskCollection<Test>,
    ): Provider<List<File>> =
        providers.provider {
            testTasks.mapNotNull { testTask ->
                testTask.extensions.findByType(JacocoTaskExtension::class.java)?.destinationFile
            }
        }

    private val freshCoverageTaskNames =
        setOf(
            "jacocoTestReport",
            "jacocoTestCoverageVerification",
            "jacocoAggregatedReport",
            "coverage",
        )
}
