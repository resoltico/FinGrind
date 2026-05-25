package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import dev.erst.fingrind.jazzer.support.JazzerTestProjectRoot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Loads committed Jazzer replay inputs from the checked-in regression corpus. */
final class CommittedRegressionSeedFixtures {
  private static final Path PROJECT_DIRECTORY = JazzerTestProjectRoot.projectDirectory();

  private CommittedRegressionSeedFixtures() {}

  static String cliRequest(String fileName) {
    return readHarnessInput(JazzerHarness.cliRequest(), fileName);
  }

  static String ledgerPlanRequest(String fileName) {
    return readHarnessInput(JazzerHarness.ledgerPlanRequest(), fileName);
  }

  static String postingWorkflow(String fileName) {
    return readHarnessInput(JazzerHarness.postingWorkflow(), fileName);
  }

  static String sqliteBookRoundTrip(String fileName) {
    return readHarnessInput(JazzerHarness.sqliteBookRoundTrip(), fileName);
  }

  private static String readHarnessInput(JazzerHarness harness, String fileName) {
    Objects.requireNonNull(harness, "harness must not be null");
    Objects.requireNonNull(fileName, "fileName must not be null");
    Path inputPath = harness.inputDirectory(PROJECT_DIRECTORY).resolve(fileName).normalize();
    try {
      return Files.readString(inputPath, StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Unable to read committed Jazzer seed input: " + inputPath, exception);
    }
  }
}
