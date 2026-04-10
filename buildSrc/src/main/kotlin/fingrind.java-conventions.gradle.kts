/**
 * Convention plugin applied to every FinGrind Java subproject.
 *
 * Centralises:
 *  - Java toolchain (version read once from gradle.properties)
 *  - sources JAR production
 *  - JUnit Platform test runner
 *  - JaCoCo report + coverage-verification tasks (100 % line, 100 % branch)
 */
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

plugins {
    `java-base`
    jacoco
}

class GradleTestPulseListener(
    private val logger: org.gradle.api.logging.Logger,
    private val taskPath: String,
    private val projectPath: String,
    pulseIntervalMillis: Long,
) : TestListener {
    private val pulseExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "fingrind-gradle-test-pulse").apply {
                isDaemon = true
            }
        }
    private val lock = Any()
    private val pulseIntervalMillis = pulseIntervalMillis.coerceAtLeast(1_000L)

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
                pulseExecutor.scheduleAtFixedRate(
                    { emitHeartbeat() },
                    pulseIntervalMillis,
                    pulseIntervalMillis,
                    TimeUnit.MILLISECONDS,
                )
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
                    "phase=finish completedClasses=$completedClasses completedTests=$completedTests failedTests=$failedTests skippedTests=$skippedTests result=${result.resultType}"
                )
            }
            pulseExecutor.shutdownNow()
            return
        }
        if (suite.className != null && suite.parent?.className == null) {
            synchronized(lock) {
                completedClasses += 1
                emit(
                    "phase=class-complete completedClasses=$completedClasses completedTests=$completedTests failedTests=$failedTests skippedTests=$skippedTests class=${pulseValue(suite.className)} classTests=${result.testCount} classFailedTests=${result.failedTestCount} classSkippedTests=${result.skippedTestCount} result=${result.resultType}"
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

    private fun pulseValue(raw: String?): String = raw?.replace(Regex("\\s+"), "_") ?: "unknown"
}

val fingrindJavaVersion: Int =
    providers.gradleProperty("fingrindJavaVersion").map(String::toInt).get()

extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion = JavaLanguageVersion.of(fingrindJavaVersion)
    }
    withSourcesJar()
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "Ervins Strauhmanis",
            "Implementation-License" to "MIT",
            "Enable-Native-Access" to "ALL-UNNAMED",
        )
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")

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
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.withType<Test>())
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.withType<Test>())
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "1.0".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "1.0".toBigDecimal()
            }
        }
    }
}
