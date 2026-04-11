package dev.erst.fingrind.jazzer.tool;

import com.code_intelligence.jazzer.driver.junit.JUnitRunner;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Launches one Jazzer harness class through the JUnit Platform outside Gradle's Test task. */
public final class JazzerHarnessRunner {
  private static final String GITHUB_ACTIONS_BLOCK_MESSAGE =
      "Active Jazzer fuzzing is local-only and must not run on GitHub Actions. "
          + "Use './gradlew -p jazzer check' for deterministic GitHub verification "
          + "and 'jazzer/bin/*' for local active fuzzing.";
  private static final String PULSE_PREFIX = "[JAZZER-PULSE] ";

  private JazzerHarnessRunner() {}

  /** Runs the requested Jazzer harness class and exits non-zero on any failure or misconfiguration. */
  public static void main(String[] args) {
    System.exit(run(parseClassName(args), standardWriter(System.out), standardWriter(System.err)));
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
    return run(className, standardWriter(System.out), errorWriter);
  }

  /** Executes one Jazzer harness class and returns a process-style exit code. */
  static int run(String className, PrintWriter outputWriter, PrintWriter errorWriter) {
    return run(
        className,
        outputWriter,
        errorWriter,
        OfficialHarnessExecutor.INSTANCE,
        JazzerHarnessRunner::runningOnGitHubActions);
  }

  /** Executes one Jazzer harness class and returns a process-style exit code. */
  static int run(
      String className,
      PrintWriter outputWriter,
      PrintWriter errorWriter,
      HarnessExecutor executor) {
    return run(
        className,
        outputWriter,
        errorWriter,
        executor,
        JazzerHarnessRunner::runningOnGitHubActions);
  }

  static int run(
      String className,
      PrintWriter outputWriter,
      PrintWriter errorWriter,
      HarnessExecutor executor,
      BooleanSupplier githubActionsDetector) {
    Objects.requireNonNull(className, "className must not be null");
    Objects.requireNonNull(outputWriter, "outputWriter must not be null");
    Objects.requireNonNull(errorWriter, "errorWriter must not be null");
    Objects.requireNonNull(executor, "executor must not be null");
    Objects.requireNonNull(githubActionsDetector, "githubActionsDetector must not be null");

    if (githubActionsDetector.getAsBoolean()) {
      errorWriter.println(GITHUB_ACTIONS_BLOCK_MESSAGE);
      return 1;
    }

    HarnessDescriptor harness;
    try {
      harness = discoverHarness(className);
    } catch (IllegalArgumentException exception) {
      errorWriter.println(exception.getMessage());
      return 1;
    }

    outputWriter.println(
        PULSE_PREFIX
            + "harness-class="
            + harness.className()
            + " phase=plan total-tests=1 fuzz-test="
            + harness.methodName());

    int exitCode;
    try {
      exitCode = executor.execute(harness);
    } catch (RuntimeException exception) {
      outputWriter.println(PULSE_PREFIX + "harness-class=" + harness.className() + " phase=finish status=FAILURE");
      errorWriter.println(exception.getMessage());
      return 1;
    }

    outputWriter.println(
        PULSE_PREFIX
            + "harness-class="
            + harness.className()
            + " phase=finish status="
            + (exitCode == 0 ? "SUCCESS" : "FAILURE")
            + " fuzz-test="
            + harness.methodName()
            + " exit-code="
            + exitCode);
    if (exitCode != 0) {
      errorWriter.println(
          "Jazzer harness execution failed for class: "
              + harness.className()
              + " (exit code "
              + exitCode
              + ")");
    }
    return exitCode;
  }

  private static boolean runningOnGitHubActions() {
    return "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"));
  }

  static HarnessDescriptor discoverHarness(String className) {
    Objects.requireNonNull(className, "className must not be null");
    Class<?> harnessClass;
    try {
      harnessClass = Class.forName(className);
    } catch (ClassNotFoundException exception) {
      throw new IllegalArgumentException("Unable to load Jazzer harness class: " + className, exception);
    }

    List<String> fuzzMethods =
        Arrays.stream(harnessClass.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(FuzzTest.class))
            .map(Method::getName)
            .sorted()
            .toList();
    if (fuzzMethods.isEmpty()) {
      throw new IllegalArgumentException("No @FuzzTest methods were declared for class: " + className);
    }
    if (fuzzMethods.size() != 1) {
      throw new IllegalArgumentException(
          "Exactly one @FuzzTest method is required per harness class: "
              + className
              + " declared "
              + fuzzMethods.size()
              + " methods ("
              + String.join(", ", fuzzMethods)
              + ")");
    }
    return new HarnessDescriptor(className, fuzzMethods.get(0));
  }

  private static PrintWriter standardWriter(OutputStream outputStream) {
    return new PrintWriter(
        new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)), true);
  }

  /** Describes the single {@code @FuzzTest} method owned by one harness class. */
  record HarnessDescriptor(String className, String methodName) {
    HarnessDescriptor {
      Objects.requireNonNull(className, "className must not be null");
      Objects.requireNonNull(methodName, "methodName must not be null");
    }
  }

  /** Executes one discovered harness descriptor and returns a process-style exit code. */
  @FunctionalInterface
  interface HarnessExecutor {
    int execute(HarnessDescriptor harness);
  }

  /** Delegates one harness launch to Jazzer's official command-line JUnit runner. */
  private static final class OfficialHarnessExecutor implements HarnessExecutor {
    private static final OfficialHarnessExecutor INSTANCE = new OfficialHarnessExecutor();

    @Override
    public int execute(HarnessDescriptor harness) {
      if (!JUnitRunner.isSupported()) {
        throw new IllegalStateException(
            "Jazzer JUnit runner support is unavailable on the harness runtime classpath");
      }
      return JUnitRunner.create(harness.className(), List.of())
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "Jazzer JUnit runner did not discover any @FuzzTest for class: "
                          + harness.className()))
          .run();
    }
  }
}
