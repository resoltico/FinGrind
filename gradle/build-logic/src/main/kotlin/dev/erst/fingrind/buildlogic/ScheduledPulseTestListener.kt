package dev.erst.fingrind.buildlogic

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.gradle.api.tasks.testing.TestListener

internal abstract class ScheduledPulseTestListener(
    threadName: String,
    interval: Long,
    private val timeUnit: TimeUnit,
) : TestListener {
    protected val lock = Any()

    private val pulseExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, threadName).apply {
                isDaemon = true
            }
        }

    private val pulseInterval: Long =
        when (timeUnit) {
            TimeUnit.MILLISECONDS -> interval.coerceAtLeast(1_000L)
            else -> interval.coerceAtLeast(1L)
        }

    protected fun startPulseLoop(action: () -> Unit) {
        pulseExecutor.scheduleAtFixedRate(action, pulseInterval, pulseInterval, timeUnit)
    }

    protected fun stopPulseLoop() {
        pulseExecutor.shutdownNow()
    }

    protected fun pulseValue(raw: String?): String = raw?.replace(Regex("\\s+"), "_") ?: "unknown"
}
