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
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Launches one Jazzer harness class through the JUnit Platform outside Gradle's Test task. */
public final class JazzerHarnessRunner {
  private static final String GITHUB_ACTIONS_BLOCK_MESSAGE =
      "Active Jazzer fuzzing is local-only and must not run on GitHub Actions. "
          + "Use 'jazzer/bin/check' for deterministic GitHub verification "
          + "and 'jazzer/bin/*' for local active fuzzing.";
  private static final String PULSE_PREFIX = "[JAZZER-PULSE] ";
  private final OutputStream outputStream;
  private final OutputStream errorStream;
  private final IntConsumer exitHandler;
  private final HarnessExecutor executor;
  private final BooleanSupplier githubActionsDetector;

  /** Creates the production harness runner backed by process streams and {@code System::exit}. */
  public JazzerHarnessRunner() {
    this(
        System.out,
        System.err,
        System::exit,
        OfficialHarnessExecutor.INSTANCE,
        JazzerHarnessRunner::runningOnGitHubActions);
  }

  JazzerHarnessRunner(
      OutputStream outputStream,
      OutputStream errorStream,
      IntConsumer exitHandler,
      HarnessExecutor executor,
      BooleanSupplier githubActionsDetector) {
    this.outputStream = Objects.requireNonNull(outputStream, "outputStream must not be null");
    this.errorStream = Objects.requireNonNull(errorStream, "errorStream must not be null");
    this.exitHandler = Objects.requireNonNull(exitHandler, "exitHandler must not be null");
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
    this.githubActionsDetector =
        Objects.requireNonNull(githubActionsDetector, "githubActionsDetector must not be null");
  }

  /**
   * Runs the requested Jazzer harness class and exits non-zero on any failure or misconfiguration.
   */
  public static void main(String[] args) {
    new JazzerHarnessRunner().run(args);
  }

  void run(String[] args) {
    try (PrintWriter outputWriter = standardWriter(outputStream);
        PrintWriter errorWriter = standardWriter(errorStream)) {
      int exitCode =
          run(parseClassName(args), outputWriter, errorWriter, executor, githubActionsDetector);
      if (exitCode != 0) {
        exitHandler.accept(exitCode);
      }
    }
  }

  /** Parses the required {@code --class <fqcn>} launcher argument pair. */
  static String parseClassName(String[] args) {
    Objects.requireNonNull(args, "args must not be null");
    if (args.length != 2 || !"--class".equals(args[0])) {
      throw new IllegalArgumentException(
          "Usage: JazzerHarnessRunner --class <fully-qualified-class>");
    }
    String className = Objects.requireNonNull(args[1], "className must not be null");
    if (className.isBlank()) {
      throw new IllegalArgumentException("className must not be blank");
    }
    return className;
  }

  /** Executes one Jazzer harness class and returns a process-style exit code. */
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
            + " event=plan total-tests=1 fuzz-test="
            + harness.methodName());

    int exitCode;
    try {
      exitCode = executor.execute(harness);
    } catch (RuntimeException exception) {
      outputWriter.println(
          PULSE_PREFIX + "harness-class=" + harness.className() + " event=finish status=FAILURE");
      errorWriter.println(exception.getMessage());
      return 1;
    }

    outputWriter.println(
        PULSE_PREFIX
            + "harness-class="
            + harness.className()
            + " event=finish status="
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

  static boolean runningOnGitHubActions() {
    return "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"));
  }

  static HarnessDescriptor discoverHarness(String className) {
    Objects.requireNonNull(className, "className must not be null");
    Class<?> harnessClass;
    try {
      harnessClass = Class.forName(className);
    } catch (ClassNotFoundException exception) {
      throw new IllegalArgumentException(
          "Unable to load Jazzer harness class: " + className, exception);
    }

    List<String> fuzzMethods =
        Arrays.stream(harnessClass.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(FuzzTest.class))
            .map(Method::getName)
            .sorted()
            .toList();
    if (fuzzMethods.isEmpty()) {
      throw new IllegalArgumentException(
          "No @FuzzTest methods were declared for class: " + className);
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
      className = ReplayModelValidation.requireText(className, "className");
      methodName = ReplayModelValidation.requireText(methodName, "methodName");
    }
  }

  /** Executes one discovered harness descriptor and returns a process-style exit code. */
  @FunctionalInterface
  interface HarnessExecutor {
    /** Runs the supplied harness descriptor and returns the resulting process-style exit code. */
    int execute(HarnessDescriptor harness);
  }

  /** Delegates one harness launch to Jazzer's official command-line JUnit runner. */
  static final class OfficialHarnessExecutor implements HarnessExecutor {
    private static final OfficialHarnessExecutor INSTANCE = new OfficialHarnessExecutor();
    private final JUnitRunnerFactory runnerFactory;

    OfficialHarnessExecutor() {
      this(
          new JUnitRunnerFactory() {
            @Override
            public boolean isSupported() {
              return JUnitRunner.isSupported();
            }

            @Override
            public java.util.Optional<IntSupplier> create(String className) {
              return JUnitRunner.create(className, List.of())
                  .map(runner -> (IntSupplier) runner::run);
            }
          });
    }

    /** Creates one official executor around the supplied Jazzer JUnit runner factory. */
    OfficialHarnessExecutor(JUnitRunnerFactory runnerFactory) {
      this.runnerFactory = Objects.requireNonNull(runnerFactory, "runnerFactory must not be null");
    }

    JUnitRunnerFactory runnerFactory() {
      return runnerFactory;
    }

    @Override
    public int execute(HarnessDescriptor harness) {
      if (!runnerFactory.isSupported()) {
        throw new IllegalStateException(
            "Jazzer JUnit runner support is unavailable on the harness runtime classpath");
      }
      return runnerFactory
          .create(harness.className())
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "Jazzer JUnit runner did not discover any @FuzzTest for class: "
                          + harness.className()))
          .getAsInt();
    }
  }

  /** Creates Jazzer's official JUnit runner when the current runtime can support it. */
  interface JUnitRunnerFactory {
    /** Returns whether the current runtime supports Jazzer's official JUnit runner. */
    boolean isSupported();

    /** Creates one runner for the supplied harness class name when discovery succeeds. */
    java.util.Optional<IntSupplier> create(String className);
  }
}
