package dev.erst.fingrind.buildlogic

import java.util.concurrent.TimeUnit
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult

internal class JazzerSupportTestPulseListener(
    private val totalClasses: Int,
    pulseIntervalSeconds: Long = 15,
) : ScheduledPulseTestListener(
        threadName = "fingrind-jazzer-support-pulse",
        interval = pulseIntervalSeconds,
        timeUnit = TimeUnit.SECONDS,
    ) {
    private var rootStarted = false
    private var rootFinished = false
    private var completedClasses = 0
    private var completedTests = 0
    private var activeTopLevelClass: String? = null
    private var activeClassTests = 0
    private var activeClassFailedTests = 0
    private var activeClassSkippedTests = 0
    private var activeTestClass: String? = null
    private var activeTestName: String? = null

    override fun beforeSuite(suite: TestDescriptor) {
        if (suite.parent != null) {
            return
        }
        synchronized(lock) {
            if (rootStarted) {
                return
            }
            rootStarted = true
            emit("support-tests phase=start total-classes=$totalClasses")
            startPulseLoop(::emitHeartbeat)
        }
    }

    override fun afterSuite(suite: TestDescriptor, result: TestResult) {
        if (suite.parent != null) {
            return
        }
        synchronized(lock) {
            finishActiveClass()
            emit(
                "support-tests phase=finish completed-classes=$completedClasses/$totalClasses completed-tests=$completedTests result=${result.resultType}",
            )
            rootFinished = true
        }
        stopPulseLoop()
    }

    override fun beforeTest(testDescriptor: TestDescriptor) {
        val topLevelClass = testDescriptor.className?.substringBefore('$') ?: return
        synchronized(lock) {
            if (activeTopLevelClass == null) {
                startClass(topLevelClass)
            } else if (activeTopLevelClass != topLevelClass) {
                finishActiveClass()
                startClass(topLevelClass)
            }
            activeTestClass = testDescriptor.className
            activeTestName = testDescriptor.name
        }
    }

    override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
        synchronized(lock) {
            completedTests += 1
            activeClassTests += 1
            when (result.resultType) {
                TestResult.ResultType.FAILURE -> activeClassFailedTests += 1
                TestResult.ResultType.SKIPPED -> activeClassSkippedTests += 1
                TestResult.ResultType.SUCCESS -> Unit
            }

            emit(
                "support-tests phase=test-complete completed-tests=$completedTests class=${pulseValue(testDescriptor.className)} name=${pulseValue(testDescriptor.name)} result=${result.resultType}",
            )

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
                    append("support-tests phase=test-progress")
                    append(" completed-tests=").append(completedTests)
                    append(" completed-classes=").append(completedClasses).append('/').append(totalClasses)
                    append(" class=").append(pulseValue(className))
                    activeTestName?.let { testName ->
                        append(" name=").append(pulseValue(testName))
                    }
                }
            }
        emit(heartbeat)
    }

    private fun startClass(topLevelClass: String) {
        activeTopLevelClass = topLevelClass
        activeClassTests = 0
        activeClassFailedTests = 0
        activeClassSkippedTests = 0
        emit("support-tests phase=class-start class=${pulseValue(topLevelClass)}")
    }

    private fun finishActiveClass() {
        val topLevelClass = activeTopLevelClass ?: return
        completedClasses += 1
        emit(
            "support-tests phase=class-complete completed-classes=$completedClasses/$totalClasses class=${pulseValue(topLevelClass)} result=${activeClassResult()}",
        )
        activeTopLevelClass = null
        activeClassTests = 0
        activeClassFailedTests = 0
        activeClassSkippedTests = 0
        activeTestClass = null
        activeTestName = null
    }

    private fun activeClassResult(): TestResult.ResultType =
        when {
            activeClassFailedTests > 0 -> TestResult.ResultType.FAILURE
            activeClassTests > 0 && activeClassSkippedTests == activeClassTests -> TestResult.ResultType.SKIPPED
            else -> TestResult.ResultType.SUCCESS
        }

    private fun emit(message: String) {
        println("[JAZZER-PULSE] $message")
    }
}
