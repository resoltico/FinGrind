package dev.erst.fingrind.jazzer.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
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
      assertThrows(IllegalArgumentException.class, () -> JazzerHarnessRunner.parseClassName(new String[0]));
    }

    @Test
    void parseClassName_throwsWhenClassNameIsBlank() {
      assertThrows(
          IllegalArgumentException.class, () -> JazzerHarnessRunner.parseClassName(new String[] {"--class", " "}));
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
          JazzerHarnessRunner.run(
              SuccessfulFuzzHarnessFixture.class.getName(),
              new PrintWriter(output, true),
              new PrintWriter(errors, true),
              harness -> {
                executedMethod[0] = harness.methodName();
                return 0;
              });

      assertEquals(0, exitCode);
      assertTrue(
          output.toString().contains(
              "[JAZZER-PULSE] harness-class="
                  + SuccessfulFuzzHarnessFixture.class.getName()
                  + " phase=plan total-tests=1 fuzz-test=fuzz"));
      assertTrue(output.toString().contains("phase=finish status=SUCCESS fuzz-test=fuzz exit-code=0"));
      assertEquals("fuzz", executedMethod[0]);
      assertTrue(errors.toString().isBlank());
    }

    @Test
    void run_returnsFailureWhenNoFuzzTestsExist() {
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();

      int exitCode =
          JazzerHarnessRunner.run(
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
          JazzerHarnessRunner.run(
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
          JazzerHarnessRunner.run(
              SuccessfulFuzzHarnessFixture.class.getName(),
              new PrintWriter(output, true),
              new PrintWriter(errors, true),
              harness -> 77);

      assertEquals(77, exitCode);
      assertTrue(output.toString().contains("phase=finish status=FAILURE fuzz-test=fuzz exit-code=77"));
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
}
