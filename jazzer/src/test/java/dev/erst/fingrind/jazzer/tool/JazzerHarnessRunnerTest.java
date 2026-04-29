package dev.erst.fingrind.jazzer.tool;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Covers argument parsing and launcher exit semantics for the Jazzer harness runner. */
class JazzerHarnessRunnerTest {
  @Nested
  class ParseClassName {
    @Test
    void parseClassName_returnsSelectedClassNameWhenArgumentsAreValid() {
      assertEquals(
          "dev.erst.fingrind.cli.CliRequestFuzzTest",
          JazzerHarnessRunner.parseClassName(
              new String[] {"--class", "dev.erst.fingrind.cli.CliRequestFuzzTest"}));
    }

    @Test
    void parseClassName_throwsWhenArgumentsAreMissing() {
      assertThrows(
          IllegalArgumentException.class, () -> JazzerHarnessRunner.parseClassName(new String[0]));
    }

    @Test
    void parseClassName_throwsWhenFlagIsInvalid() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              JazzerHarnessRunner.parseClassName(
                  new String[] {"--wrong", SuccessfulFuzzHarnessFixture.class.getName()}));
    }

    @Test
    void parseClassName_throwsWhenClassNameIsBlank() {
      assertThrows(
          IllegalArgumentException.class,
          () -> JazzerHarnessRunner.parseClassName(new String[] {"--class", " "}));
    }

    @Test
    void parseClassName_throwsWhenClassNameIsMissing() {
      assertThrows(
          IllegalArgumentException.class,
          () -> JazzerHarnessRunner.parseClassName(new String[] {"--class"}));
    }
  }

  @Nested
  class Run {
    @Test
    void run_returnsSuccessWhenExactlyOneFuzzTestExists() {
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();
      String[] executedMethod = new String[1];

      int exitCode =
          runIgnoringGitHubActions(
              SuccessfulFuzzHarnessFixture.class.getName(),
              new PrintWriter(output, true),
              new PrintWriter(errors, true),
              harness -> {
                executedMethod[0] = harness.methodName();
                return 0;
              });

      assertEquals(0, exitCode);
      assertTrue(
          output
              .toString()
              .contains(
                  "[JAZZER-PULSE] harness-class="
                      + SuccessfulFuzzHarnessFixture.class.getName()
                      + " phase=plan total-tests=1 fuzz-test=fuzz"));
      assertTrue(
          output.toString().contains("phase=finish status=SUCCESS fuzz-test=fuzz exit-code=0"));
      assertEquals("fuzz", executedMethod[0]);
      assertTrue(errors.toString().isBlank());
    }

    @Test
    void run_returnsFailureWhenNoFuzzTestsExist() {
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();

      int exitCode =
          runIgnoringGitHubActions(
              NonFuzzHarnessFixture.class.getName(),
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(1, exitCode);
      assertTrue(output.toString().isBlank());
      assertTrue(errors.toString().contains("No @FuzzTest methods were declared"));
    }

    @Test
    void run_returnsFailureWhenMultipleFuzzTestsExist() {
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();

      int exitCode =
          runIgnoringGitHubActions(
              MultiFuzzHarnessFixture.class.getName(),
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(1, exitCode);
      assertTrue(output.toString().isBlank());
      assertTrue(errors.toString().contains("Exactly one @FuzzTest method is required"));
      assertTrue(errors.toString().contains("alpha"));
      assertTrue(errors.toString().contains("beta"));
    }

    @Test
    void run_returnsFailureWhenExecutorFails() {
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();

      int exitCode =
          runIgnoringGitHubActions(
              SuccessfulFuzzHarnessFixture.class.getName(),
              new PrintWriter(output, true),
              new PrintWriter(errors, true),
              harness -> 77);

      assertEquals(77, exitCode);
      assertTrue(
          output.toString().contains("phase=finish status=FAILURE fuzz-test=fuzz exit-code=77"));
      assertTrue(errors.toString().contains("exit code 77"));
    }

    @Test
    void run_returnsFailureWhenGitHubActionsIsDetected() {
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();

      int exitCode =
          JazzerHarnessRunner.run(
              SuccessfulFuzzHarnessFixture.class.getName(),
              new PrintWriter(output, true),
              new PrintWriter(errors, true),
              harness -> {
                throw new AssertionError("executor must not run on GitHub Actions");
              },
              () -> true);

      assertEquals(1, exitCode);
      assertTrue(output.toString().isBlank());
      assertTrue(errors.toString().contains("must not run on GitHub Actions"));
      assertTrue(errors.toString().contains("'jazzer/bin/*'"));
    }

    @Test
    void run_returnsFailureWhenHarnessClassCannotBeLoaded() {
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();

      int exitCode =
          runIgnoringGitHubActions(
              "dev.erst.fingrind.jazzer.tool.MissingHarnessFixture",
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(1, exitCode);
      assertTrue(output.toString().isBlank());
      assertTrue(errors.toString().contains("Unable to load Jazzer harness class"));
    }

    @Test
    void run_returnsFailureWhenExecutorThrows() {
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();

      int exitCode =
          runIgnoringGitHubActions(
              SuccessfulFuzzHarnessFixture.class.getName(),
              new PrintWriter(output, true),
              new PrintWriter(errors, true),
              harness -> {
                throw new IllegalStateException("executor boom");
              });

      assertEquals(1, exitCode);
      assertTrue(output.toString().contains("phase=finish status=FAILURE"));
      assertTrue(errors.toString().contains("executor boom"));
    }

    @Test
    void run_overloadUsingDefaultOutputStreamStillReportsErrors() {
      StringWriter errors = new StringWriter();

      AtomicInteger exitCode = new AtomicInteger(-1);
      JazzerHarnessRunner.withProductionWiringForTesting(
          new NoOpHarnessExecutor(),
          () -> false,
          () ->
              exitCode.set(
                  JazzerHarnessRunner.run(
                      "dev.erst.fingrind.jazzer.tool.MissingHarnessFixture",
                      new PrintWriter(errors, true))));

      assertEquals(1, exitCode.get());
      assertTrue(errors.toString().contains("Unable to load Jazzer harness class"));
    }

    @Test
    void instanceRun_skipsExitHandlerOnSuccessWhenInjectedExecutorSucceeds() {
      AtomicInteger exitCode = new AtomicInteger(-1);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      ByteArrayOutputStream errors = new ByteArrayOutputStream();

      new JazzerHarnessRunner(output, errors, exitCode::set, harness -> 0, () -> false)
          .run(new String[] {"--class", SuccessfulFuzzHarnessFixture.class.getName()});

      assertEquals(-1, exitCode.get());
      assertTrue(
          output
              .toString(StandardCharsets.UTF_8)
              .contains("phase=finish status=SUCCESS fuzz-test=fuzz exit-code=0"));
      assertTrue(errors.toString(StandardCharsets.UTF_8).isBlank());
    }
  }

  @Nested
  class DiscoverHarness {
    @Test
    void discoverHarness_returnsSelectedFuzzMethod() {
      JazzerHarnessRunner.HarnessDescriptor descriptor =
          JazzerHarnessRunner.discoverHarness(SuccessfulFuzzHarnessFixture.class.getName());

      assertEquals(SuccessfulFuzzHarnessFixture.class.getName(), descriptor.className());
      assertEquals("fuzz", descriptor.methodName());
    }
  }

  @Test
  void instanceRun_callsExitHandlerForFailures_and_mainRunsEndToEnd() {
    AtomicInteger exitCode = new AtomicInteger(-1);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ByteArrayOutputStream errors = new ByteArrayOutputStream();

    JazzerHarnessRunner.withProductionWiringForTesting(
        new NoOpHarnessExecutor(),
        () -> false,
        () ->
            new JazzerHarnessRunner(output, errors, exitCode::set)
                .run(new String[] {"--class", NonFuzzHarnessFixture.class.getName()}));

    assertEquals(1, exitCode.get());
    assertTrue(
        errors.toString(StandardCharsets.UTF_8).contains("No @FuzzTest methods were declared"));
  }

  @Test
  void main_runsEndToEndForMisconfiguredHarnessWithoutCallingSystemExit() {
    try {
      JazzerHarnessRunner.main(new String[] {"--class", " "});
      throw new AssertionError("Expected invalid harness arguments to fail fast.");
    } catch (IllegalArgumentException exception) {
      assertTrue(String.valueOf(exception.getMessage()).contains("className must not be blank"));
    }
  }

  @Test
  void main_returnsNormallyWhenOfficialExecutorSucceeds() {
    assertDoesNotThrow(
        () ->
            JazzerHarnessRunner.withProductionWiringForTesting(
                new JazzerHarnessRunner.OfficialHarnessExecutor(
                    new TestRunnerFactory(true, Optional.of(() -> 0))),
                () -> false,
                () ->
                    JazzerHarnessRunner.main(
                        new String[] {"--class", SuccessfulFuzzHarnessFixture.class.getName()})));
  }

  @Test
  void officialHarnessExecutor_enforces_runner_support_and_discovery() {
    JazzerHarnessRunner.OfficialHarnessExecutor unsupportedExecutor =
        new JazzerHarnessRunner.OfficialHarnessExecutor(
            new TestRunnerFactory(false, Optional.empty()));
    JazzerHarnessRunner.OfficialHarnessExecutor missingRunnerExecutor =
        new JazzerHarnessRunner.OfficialHarnessExecutor(
            new TestRunnerFactory(true, Optional.empty()));
    JazzerHarnessRunner.OfficialHarnessExecutor successExecutor =
        new JazzerHarnessRunner.OfficialHarnessExecutor(
            new TestRunnerFactory(true, Optional.of(() -> 17)));

    IllegalStateException unsupported =
        assertThrows(
            IllegalStateException.class,
            () ->
                unsupportedExecutor.execute(
                    new JazzerHarnessRunner.HarnessDescriptor(
                        SuccessfulFuzzHarnessFixture.class.getName(), "fuzz")));
    assertTrue(String.valueOf(unsupported.getMessage()).contains("support is unavailable"));

    IllegalStateException missingRunner =
        assertThrows(
            IllegalStateException.class,
            () ->
                missingRunnerExecutor.execute(
                    new JazzerHarnessRunner.HarnessDescriptor(
                        SuccessfulFuzzHarnessFixture.class.getName(), "fuzz")));
    assertTrue(
        String.valueOf(missingRunner.getMessage()).contains("did not discover any @FuzzTest"));

    assertEquals(
        17,
        successExecutor.execute(
            new JazzerHarnessRunner.HarnessDescriptor(
                SuccessfulFuzzHarnessFixture.class.getName(), "fuzz")));
  }

  @Test
  void officialRunner_defaultFactory_is_reachable_without_launching_fuzzing() throws Exception {
    Constructor<JazzerHarnessRunner.OfficialHarnessExecutor> constructor =
        JazzerHarnessRunner.OfficialHarnessExecutor.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    JazzerHarnessRunner.OfficialHarnessExecutor executor = constructor.newInstance();
    Field runnerFactoryField =
        JazzerHarnessRunner.OfficialHarnessExecutor.class.getDeclaredField("runnerFactory");
    runnerFactoryField.setAccessible(true);
    JazzerHarnessRunner.JUnitRunnerFactory runnerFactory =
        (JazzerHarnessRunner.JUnitRunnerFactory) runnerFactoryField.get(executor);

    assertEquals(
        com.code_intelligence.jazzer.driver.junit.JUnitRunner.isSupported(),
        runnerFactory.isSupported());
    assertDoesNotThrow(() -> runnerFactory.create(OneShotFuzzHarness.class.getName()));
    if (runnerFactory.isSupported()) {
      assertTrue(runnerFactory.create(OneShotFuzzHarness.class.getName()).isPresent());
    }
  }

  @Test
  void run_overloadUsingExplicitExecutor_stillUsesProductionDetector() {
    StringWriter output = new StringWriter();
    StringWriter errors = new StringWriter();
    AtomicInteger exitCode = new AtomicInteger(-1);

    JazzerHarnessRunner.withProductionWiringForTesting(
        new NoOpHarnessExecutor(),
        () -> false,
        () ->
            exitCode.set(
                JazzerHarnessRunner.run(
                    SuccessfulFuzzHarnessFixture.class.getName(),
                    new PrintWriter(output, true),
                    new PrintWriter(errors, true),
                    harness -> 23)));

    assertEquals(23, exitCode.get());
    assertTrue(
        output.toString().contains("phase=finish status=FAILURE fuzz-test=fuzz exit-code=23"));
    assertTrue(errors.toString().contains("exit code 23"));
  }

  @Test
  void runningOnGitHubActions_matchesProcessEnvironment() throws Exception {
    Method method = JazzerHarnessRunner.class.getDeclaredMethod("runningOnGitHubActions");
    method.setAccessible(true);

    assertEquals("true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS")), method.invoke(null));
  }

  /** One-shot harness fixture used only to prove official runner discovery remains reachable. */
  static final class OneShotFuzzHarness extends SingleExecutionFuzzHarnessFixture {}

  private record TestRunnerFactory(
      boolean supported, Optional<JazzerHarnessRunner.JazzerRunner> runner)
      implements JazzerHarnessRunner.JUnitRunnerFactory {
    @Override
    public boolean isSupported() {
      return supported;
    }

    @Override
    public Optional<JazzerHarnessRunner.JazzerRunner> create(String className) {
      return runner;
    }
  }

  private static int runIgnoringGitHubActions(
      String className, PrintWriter outputWriter, PrintWriter errorWriter) {
    return JazzerHarnessRunner.run(
        className, outputWriter, errorWriter, new NoOpHarnessExecutor(), () -> false);
  }

  private static int runIgnoringGitHubActions(
      String className,
      PrintWriter outputWriter,
      PrintWriter errorWriter,
      JazzerHarnessRunner.HarnessExecutor executor) {
    return JazzerHarnessRunner.run(className, outputWriter, errorWriter, executor, () -> false);
  }

  private static final class NoOpHarnessExecutor implements JazzerHarnessRunner.HarnessExecutor {
    @Override
    public int execute(JazzerHarnessRunner.HarnessDescriptor harness) {
      throw new AssertionError("executor must not run");
    }
  }
}
