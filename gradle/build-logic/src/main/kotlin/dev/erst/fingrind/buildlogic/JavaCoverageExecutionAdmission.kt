package dev.erst.fingrind.buildlogic

import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestFilter

/** Admits only fresh, complete test execution as evidence for a JaCoCo report. */
internal object JavaCoverageExecutionAdmission {
    fun requireCompleteFreshTestRun(
        reportTaskPath: String,
        expectedTestTaskPaths: Set<String>,
        executedTestTaskPaths: Set<String>,
        testTaskSelectionRestrictions: Map<String, List<String>>,
    ) {
        val omittedTestTaskPaths = expectedTestTaskPaths - executedTestTaskPaths
        check(omittedTestTaskPaths.isEmpty()) {
            "JaCoCo coverage report $reportTaskPath cannot use stale or incomplete execution data: " +
                "these required Test task(s) did not run in this Gradle invocation: " +
                omittedTestTaskPaths.joinToString(separator = ", ") + ". " +
                "Run coverage without excluding test tasks and with fresh test execution."
        }
        val filteredTestTasks =
            testTaskSelectionRestrictions
                .filterValues { restrictions -> restrictions.isNotEmpty() }
                .toSortedMap()
        check(filteredTestTasks.isEmpty()) {
            "JaCoCo coverage report rejects filtered Test task(s): " +
                filteredTestTasks.entries.joinToString(separator = "; ") { (taskPath, restrictions) ->
                    val formattedRestrictions = restrictions.joinToString(separator = ", ")
                    "$taskPath: $formattedRestrictions"
                } + ". Run coverage without --tests or Test include/exclude filters."
        }
    }

    fun testSelectionRestrictions(testTask: Test): List<String> =
        buildList {
            addRestriction("file include", testTask.includes)
            addRestriction("file exclude", testTask.excludes)
            addRestriction("test include", testTask.filter.includePatterns)
            addRestriction("test exclude", testTask.filter.excludePatterns)
            addRestriction(
                "command-line test include",
                commandLineIncludePatterns(testTask.filter),
            )
        }

    fun requireEveryDirectProductionJavaProjectAggregated(
        directProductionJavaProjectPaths: Set<String>,
        aggregatedProductionJavaProjectPaths: Set<String>,
    ) {
        val omittedProjectPaths =
            (directProductionJavaProjectPaths - aggregatedProductionJavaProjectPaths).toSortedSet()
        check(omittedProjectPaths.isEmpty()) {
            "Root JaCoCo aggregation omits direct production Java project(s): " +
                omittedProjectPaths.joinToString(separator = ", ") + ". " +
                "Every direct production Java project must apply dev.erst.fingrind.java-conventions."
        }
    }

    private fun MutableList<String>.addRestriction(label: String, patterns: Set<String>) {
        if (patterns.isNotEmpty()) {
            add("$label [${patterns.toSortedSet().joinToString(separator = ", ")}]")
        }
    }

    private fun commandLineIncludePatterns(testFilter: TestFilter): Set<String> {
        val accessor =
            testFilter.javaClass.methods.singleOrNull { method ->
                method.name == commandLineIncludePatternsAccessorName && method.parameterCount == 0
            }
                ?: throw IllegalStateException(
                    "JaCoCo coverage verification cannot inspect command-line test selection on " +
                        "${testFilter.javaClass.name}; it must fail rather than accept potentially filtered coverage.",
                )
        val result =
            try {
                accessor.invoke(testFilter)
            } catch (exception: ReflectiveOperationException) {
                throw IllegalStateException(
                    "JaCoCo coverage verification could not inspect command-line test selection on " +
                        "${testFilter.javaClass.name}; it must fail rather than accept potentially filtered coverage.",
                    exception,
                )
            }
        check(result is Set<*>) {
            "JaCoCo coverage verification received an invalid command-line test selection from " +
                "${testFilter.javaClass.name}; it must fail rather than accept potentially filtered coverage."
        }
        return result.map { pattern ->
            check(pattern is String) {
                "JaCoCo coverage verification received a non-string command-line test selection from " +
                    "${testFilter.javaClass.name}; it must fail rather than accept potentially filtered coverage."
            }
            pattern
        }.toSet()
    }

    private const val commandLineIncludePatternsAccessorName = "getCommandLineIncludePatterns"
}
