package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Replays one harness's committed seeds directly against FinGrind's replay engine. */
public final class JazzerRegressionRunner {
  private static final String PULSE_PREFIX = "[JAZZER-PULSE] ";

  private JazzerRegressionRunner() {}

  /** Replays the selected harness's committed seeds and exits non-zero on any mismatch. */
  public static void main(String[] args) throws IOException {
    try (PrintWriter outputWriter = new PrintWriter(System.out, true);
        PrintWriter errorWriter = new PrintWriter(System.err, true)) {
      System.exit(run(Path.of("").toAbsolutePath().normalize(), parseHarness(args), outputWriter, errorWriter));
    }
  }

  /** Parses the required {@code --target <harness-key>} argument pair for direct regression replay. */
  static JazzerHarness parseHarness(String[] args) {
    Objects.requireNonNull(args, "args must not be null");
    if (args.length != 2 || !"--target".equals(args[0])) {
      throw new IllegalArgumentException("Usage: JazzerRegressionRunner --target <harness-key>");
    }
    String targetKey = Objects.requireNonNull(args[1], "targetKey must not be null");
    if (targetKey.isBlank()) {
      throw new IllegalArgumentException("targetKey must not be blank");
    }
    return JazzerHarness.fromKey(targetKey);
  }

  /** Replays all committed seeds for one harness and returns a process-style exit code. */
  static int run(
      Path projectDirectory, JazzerHarness harness, PrintWriter outputWriter, PrintWriter errorWriter)
      throws IOException {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    Objects.requireNonNull(harness, "harness must not be null");
    Objects.requireNonNull(outputWriter, "outputWriter must not be null");
    Objects.requireNonNull(errorWriter, "errorWriter must not be null");

    List<Path> metadataPaths = RegressionSeedSupport.metadataPaths(projectDirectory, harness);
    if (metadataPaths.isEmpty()) {
      errorWriter.println("No regression metadata entries were found for harness: " + harness.key());
      return 1;
    }

    outputWriter.println(
        PULSE_PREFIX
            + "regression-target phase=plan target="
            + harness.key()
            + " total-inputs="
            + metadataPaths.size());

    for (int index = 0; index < metadataPaths.size(); index++) {
      Path metadataPath = metadataPaths.get(index);
      RegressionSeedMetadata metadata = JazzerJson.read(metadataPath, RegressionSeedMetadata.class);
      if (!metadata.targetKey().equals(harness.key())) {
        errorWriter.println(
            "Regression metadata target mismatch for "
                + metadataPath.getFileName()
                + ": expected "
                + harness.key()
                + " but was "
                + metadata.targetKey());
        return 1;
      }
      Path inputPath = metadata.inputPath(projectDirectory);
      if (!Files.exists(inputPath)) {
        errorWriter.println("Committed regression input does not exist: " + inputPath);
        return 1;
      }
      ReplayOutcome outcome = JazzerReplaySupport.replay(harness, Files.readAllBytes(inputPath));
      ReplayExpectation actualExpectation = JazzerReplaySupport.expectationFor(outcome);
      if (!metadata.expectation().equals(actualExpectation)) {
        errorWriter.println(
            "Regression mismatch for "
                + harness.key()
                + " input "
                + inputPath.getFileName()
                + ": expected "
                + JazzerJson.toJson(metadata.expectation())
                + " but got "
                + JazzerJson.toJson(actualExpectation));
        if (outcome instanceof ReplayOutcome.UnexpectedFailure unexpectedFailure) {
          errorWriter.println(unexpectedFailure.stackTrace());
        }
        return 1;
      }
      outputWriter.println(
          PULSE_PREFIX
              + "regression-input target="
              + harness.key()
              + " completed="
              + (index + 1)
              + "/"
              + metadataPaths.size()
              + " name="
              + inputPath.getFileName()
              + " status=SUCCESS");
    }

    outputWriter.println(
        PULSE_PREFIX + "regression-target phase=finish target=" + harness.key() + " status=SUCCESS");
    return 0;
  }
}
