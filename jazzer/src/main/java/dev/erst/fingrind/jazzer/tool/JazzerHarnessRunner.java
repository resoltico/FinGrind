package dev.erst.fingrind.jazzer.tool;

import java.io.PrintWriter;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/** Launches one Jazzer harness class through the JUnit Platform outside Gradle's Test task. */
public final class JazzerHarnessRunner {
  private static final long DEFAULT_PULSE_INTERVAL_MILLIS = 15_000L;
  private static final String PULSE_PREFIX = "[JAZZER-PULSE] ";

  private JazzerHarnessRunner() {}

  /** Runs the requested Jazzer harness class and exits non-zero on any failure or misconfiguration. */
  public static void main(String[] args) {
    try (PrintWriter outputWriter = new PrintWriter(System.out, true);
        PrintWriter errorWriter = new PrintWriter(System.err, true)) {
      System.exit(run(parseClassName(args), outputWriter, errorWriter));
    }
  }

  /** Parses the required {@code --class <fqcn>} launcher argument pair. */
  static String parseClassName(String[] args) {
    Objects.requireNonNull(args, "args must not be null");
    if (args.length != 2 || !"--class".equals(args[0])) {
      throw new IllegalArgumentException("Usage: JazzerHarnessRunner --class <fully-qualified-class>");
    }
    String className = Objects.requireNonNull(args[1], "className must not be null");
    if (className.isBlank()) {
      throw new IllegalArgumentException("className must not be blank");
    }
    return className;
  }

  /** Executes one Jazzer harness class and returns a process-style exit code. */
  static int run(String className, PrintWriter errorWriter) {
    return run(className, new PrintWriter(System.out, true), errorWriter);
  }

  /** Executes one Jazzer harness class and returns a process-style exit code. */
  static int run(String className, PrintWriter outputWriter, PrintWriter errorWriter) {
    return run(className, outputWriter, errorWriter, DEFAULT_PULSE_INTERVAL_MILLIS);
  }

  /** Executes one Jazzer harness class and returns a process-style exit code. */
  static int run(
      String className,
      PrintWriter outputWriter,
      PrintWriter errorWriter,
      long pulseIntervalMillis) {
    Objects.requireNonNull(className, "className must not be null");
    Objects.requireNonNull(outputWriter, "outputWriter must not be null");
    Objects.requireNonNull(errorWriter, "errorWriter must not be null");
    TestExecutionSummary summary = execute(className, outputWriter, pulseIntervalMillis);
    outputWriter.println(
        PULSE_PREFIX
            + "harness-class="
            + className
            + " phase=finish completed="
            + summary.getTestsSucceededCount()
            + "/"
            + summary.getTestsFoundCount()
            + " status="
            + finalStatus(summary));
    if (summary.getTestsFoundCount() == 0) {
      errorWriter.println("No Jazzer tests were discovered for class: " + className);
      return 1;
    }
    if (summary.getTotalFailureCount() > 0) {
      summary.printFailuresTo(errorWriter);
      return 1;
    }
    return 0;
  }

  private static String finalStatus(TestExecutionSummary summary) {
    if (summary.getTestsFoundCount() == 0) {
      return "NO_TESTS";
    }
    if (summary.getTotalFailureCount() > 0) {
      return "FAILURE";
    }
    return "SUCCESS";
  }

  private static TestExecutionSummary execute(
      String className, PrintWriter outputWriter, long pulseIntervalMillis) {
    LauncherDiscoveryRequest discoveryRequest =
        LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(className))
            .build();
    SummaryGeneratingListener listener = new SummaryGeneratingListener();
    Launcher launcher = LauncherFactory.create();
    launcher.registerTestExecutionListeners(
        listener, new PulseListener(className, outputWriter, pulseIntervalMillis));
    launcher.execute(discoveryRequest);
    return listener.getSummary();
  }

  /** Emits concise per-harness progress pulses during standalone Jazzer launcher execution. */
  private static final class PulseListener implements TestExecutionListener {
    private final String className;
    private final PrintWriter outputWriter;
    private final ScheduledExecutorService pulseExecutor;
    private final Object lock = new Object();
    private long totalTests;
    private long completedTests;
    private boolean planStarted;
    private boolean planFinished;
    private String activeTestDisplayName;

    private PulseListener(String className, PrintWriter outputWriter, long pulseIntervalMillis) {
      this.className = Objects.requireNonNull(className, "className must not be null");
      this.outputWriter = Objects.requireNonNull(outputWriter, "outputWriter must not be null");
      long normalizedPulseIntervalMillis = Math.max(1L, pulseIntervalMillis);
      this.pulseExecutor =
          Executors.newSingleThreadScheduledExecutor(
              new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                  Thread thread = new Thread(runnable, "fingrind-jazzer-harness-pulse");
                  thread.setDaemon(true);
                  return thread;
                }
              });
      this.pulseExecutor.scheduleAtFixedRate(
          new Runnable() {
            @Override
            public void run() {
              emitHeartbeat();
            }
          },
          normalizedPulseIntervalMillis,
          normalizedPulseIntervalMillis,
          TimeUnit.MILLISECONDS);
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
      synchronized (lock) {
        totalTests = testPlan.countTestIdentifiers(TestIdentifier::isTest);
        planStarted = true;
        emit(
            PULSE_PREFIX
                + "harness-class="
                + className
                + " phase=plan total-tests="
                + totalTests);
      }
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
      if (!testIdentifier.isTest()) {
        return;
      }
      synchronized (lock) {
        activeTestDisplayName = normalizedDisplayName(testIdentifier);
      }
    }

    @Override
    public void executionFinished(
        TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
      if (!testIdentifier.isTest()) {
        return;
      }
      synchronized (lock) {
        completedTests += 1;
        String displayName = normalizedDisplayName(testIdentifier);
        emit(
            PULSE_PREFIX
                + "harness-class="
                + className
                + " phase=test-complete completed="
                + completedTests
                + "/"
                + displayTotalTests()
                + " status="
                + testExecutionResult.getStatus()
                + " test="
                + displayName);
        if (Objects.equals(activeTestDisplayName, displayName)) {
          activeTestDisplayName = null;
        }
      }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
      synchronized (lock) {
        planFinished = true;
        activeTestDisplayName = null;
      }
      pulseExecutor.shutdownNow();
    }

    private void emitHeartbeat() {
      synchronized (lock) {
        if (!planStarted || planFinished) {
          return;
        }
        StringBuilder pulse =
            new StringBuilder()
                .append(PULSE_PREFIX)
                .append("harness-class=")
                .append(className)
                .append(" phase=test-progress completed=")
                .append(completedTests)
                .append("/")
                .append(displayTotalTests());
        if (activeTestDisplayName != null) {
          pulse.append(" test=").append(activeTestDisplayName);
        }
        emit(pulse.toString());
      }
    }

    private String normalizedDisplayName(TestIdentifier testIdentifier) {
      return testIdentifier.getDisplayName().replaceAll("\\s+", " ").trim();
    }

    private long displayTotalTests() {
      return Math.max(totalTests, completedTests);
    }

    private void emit(String message) {
      outputWriter.println(message);
    }
  }
}
