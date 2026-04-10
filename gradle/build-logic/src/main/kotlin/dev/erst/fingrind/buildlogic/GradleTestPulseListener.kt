package dev.erst.fingrind.buildlogic

import java.util.concurrent.TimeUnit
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult

internal class GradleTestPulseListener(
    private val logger: Logger,
    private val taskPath: String,
    private val projectPath: String,
    pulseIntervalMillis: Long,
) : ScheduledPulseTestListener(
        threadName = "fingrind-gradle-test-pulse",
        interval = pulseIntervalMillis,
        timeUnit = TimeUnit.MILLISECONDS,
    ) {
    private var rootStarted = false
    private var rootFinished = false
    private var completedClasses = 0L
    private var completedTests = 0L
    private var failedTests = 0L
    private var skippedTests = 0L
    private var activeTopLevelClass: String? = null
    private var activeTestClass: String? = null
    private var activeTestName: String? = null

    override fun beforeSuite(suite: TestDescriptor) {
        if (suite.parent == null) {
            synchronized(lock) {
                if (rootStarted) {
                    return
                }
                rootStarted = true
                emit("phase=start")
                startPulseLoop(::emitHeartbeat)
            }
            return
        }
        if (suite.className != null && suite.parent?.className == null) {
            synchronized(lock) {
                activeTopLevelClass = suite.className
                emit("phase=class-start class=${pulseValue(suite.className)}")
            }
        }
    }

    override fun afterSuite(suite: TestDescriptor, result: TestResult) {
        if (suite.parent == null) {
            synchronized(lock) {
                rootFinished = true
                emit(
                    "phase=finish completedClasses=$completedClasses completedTests=$completedTests failedTests=$failedTests skippedTests=$skippedTests result=${result.resultType}",
                )
            }
            stopPulseLoop()
            return
        }
        if (suite.className != null && suite.parent?.className == null) {
            synchronized(lock) {
                completedClasses += 1
                emit(
                    "phase=class-complete completedClasses=$completedClasses completedTests=$completedTests failedTests=$failedTests skippedTests=$skippedTests class=${pulseValue(suite.className)} classTests=${result.testCount} classFailedTests=${result.failedTestCount} classSkippedTests=${result.skippedTestCount} result=${result.resultType}",
                )
                if (activeTopLevelClass == suite.className) {
                    activeTopLevelClass = null
                }
                activeTestClass = null
                activeTestName = null
            }
        }
    }

    override fun beforeTest(testDescriptor: TestDescriptor) {
        synchronized(lock) {
            activeTestClass = testDescriptor.className
            activeTestName = testDescriptor.name
            if (activeTopLevelClass == null) {
                activeTopLevelClass = testDescriptor.className
            }
        }
    }

    override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
        synchronized(lock) {
            completedTests += 1
            when (result.resultType) {
                TestResult.ResultType.FAILURE -> failedTests += 1
                TestResult.ResultType.SKIPPED -> skippedTests += 1
                TestResult.ResultType.SUCCESS -> Unit
            }
            if (activeTestClass == testDescriptor.className && activeTestName == testDescriptor.name) {
                activeTestClass = null
                activeTestName = null
            }
        }
    }

    private fun emitHeartbeat() {
        val heartbeat =
            synchronized(lock) {
                if (!rootStarted || rootFinished) {
                    return
                }
                val className = activeTestClass ?: activeTopLevelClass ?: return
                buildString {
                    append("phase=test-progress")
                    append(" completedClasses=").append(completedClasses)
                    append(" completedTests=").append(completedTests)
                    append(" failedTests=").append(failedTests)
                    append(" skippedTests=").append(skippedTests)
                    append(" class=").append(pulseValue(className))
                    activeTestName?.let { testName ->
                        append(" test=").append(pulseValue(testName))
                    }
                }
            }
        emit(heartbeat)
    }

    private fun emit(message: String) {
        logger.lifecycle(
            "[GRADLE-TEST-PULSE] task={} project={} {}",
            taskPath,
            projectPath,
            message,
        )
    }
}
