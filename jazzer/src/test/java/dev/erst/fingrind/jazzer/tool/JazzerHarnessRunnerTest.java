package dev.erst.fingrind.jazzer.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
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
    void run_returnsSuccessWhenTaggedJazzerTestsExist() {
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();

      int exitCode =
          JazzerHarnessRunner.run(
              SuccessfulTaggedHarness.class.getName(),
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(0, exitCode);
      assertTrue(
          output.toString().contains(
              "[JAZZER-PULSE] harness-class="
                  + SuccessfulTaggedHarness.class.getName()
                  + " phase=plan total-tests=1"));
      assertTrue(output.toString().contains("phase=test-complete completed=1/1 status=SUCCESS"));
      assertTrue(output.toString().contains("phase=finish completed=1/1 status=SUCCESS"));
      assertTrue(errors.toString().isBlank());
    }

    @Test
    void run_returnsFailureWhenSelectedClassContainsNoTests() {
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();

      int exitCode =
          JazzerHarnessRunner.run(
              NoTestsHarness.class.getName(),
              new PrintWriter(output, true),
              new PrintWriter(errors, true));

      assertEquals(1, exitCode);
      assertTrue(
          output.toString().contains(
              "[JAZZER-PULSE] harness-class="
                  + NoTestsHarness.class.getName()
                  + " phase=plan total-tests=0"));
      assertTrue(output.toString().contains("phase=finish completed=0/0 status=NO_TESTS"));
      assertTrue(errors.toString().contains("No Jazzer tests were discovered"));
    }

    @Test
    void run_emitsProgressHeartbeatWhileTaggedHarnessIsStillRunning() {
      StringWriter output = new StringWriter();
      StringWriter errors = new StringWriter();

      int exitCode =
          JazzerHarnessRunner.run(
              SlowTaggedHarness.class.getName(),
              new PrintWriter(output, true),
              new PrintWriter(errors, true),
              5);

      assertEquals(0, exitCode);
      assertTrue(output.toString().contains("phase=test-progress completed=0/1"));
      assertTrue(output.toString().contains("phase=finish completed=1/1 status=SUCCESS"));
      assertTrue(errors.toString().isBlank());
    }
  }

  @Tag("jazzer")
  static class SuccessfulTaggedHarness {
    @Test
    void succeeds() {}
  }

  @Tag("jazzer")
  static class SlowTaggedHarness {
    @Test
    @SuppressWarnings("PMD.DoNotUseThreads")
    void succeedsSlowly() throws InterruptedException {
      Thread.sleep(75);
    }
  }

  static class NoTestsHarness {}
}
