package dev.erst.fingrind.buildlogic

import java.util.concurrent.ConcurrentHashMap
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/** Records the Test tasks that actually ran in this Gradle invocation. */
internal abstract class JavaCoverageExecutionLedger :
    BuildService<BuildServiceParameters.None>,
    AutoCloseable {
    private val executedTestTaskSelectionRestrictions = ConcurrentHashMap<String, List<String>>()

    fun recordTestExecution(
        testTaskPath: String,
        selectionRestrictions: List<String>,
    ) {
        executedTestTaskSelectionRestrictions[testTaskPath] = selectionRestrictions
    }

    fun executedTestTaskPaths(): Set<String> = executedTestTaskSelectionRestrictions.keys.toSet()

    fun testTaskSelectionRestrictions(): Map<String, List<String>> =
        executedTestTaskSelectionRestrictions.toMap()

    override fun close() = Unit
}
