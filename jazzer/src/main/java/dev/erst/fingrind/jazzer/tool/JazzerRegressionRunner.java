package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerHarness;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Replays one harness's committed seeds directly against FinGrind's replay engine. */
public final class JazzerRegressionRunner {
  private static final String PULSE_PREFIX = "[JAZZER-PULSE] ";
  private static final String PROJECT_ROOT_OPTION = "--project-root";
  private final Path projectDirectory;
  private final OutputStream outputStream;
  private final OutputStream errorStream;
  private final ExitHandler exitHandler;

  JazzerRegressionRunner(
      Path projectDirectory,
      OutputStream outputStream,
      OutputStream errorStream,
      ExitHandler exitHandler) {
    this.projectDirectory =
        Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    this.outputStream = Objects.requireNonNull(outputStream, "outputStream must not be null");
    this.errorStream = Objects.requireNonNull(errorStream, "errorStream must not be null");
    this.exitHandler = Objects.requireNonNull(exitHandler, "exitHandler must not be null");
  }

  /** Replays the selected harness's committed seeds and exits non-zero on any mismatch. */
  public static void main(String[] args) throws IOException {
    runMain(args, System.out, System.err, System::exit);
  }

  static void runMain(
      String[] args, OutputStream outputStream, OutputStream errorStream, ExitHandler exitHandler)
      throws IOException {
    MainArguments mainArguments = MainArguments.parse(args);
    new JazzerRegressionRunner(
            mainArguments.projectDirectory(), outputStream, errorStream, exitHandler)
        .runHarness(parseHarness(mainArguments.commandArguments().toArray(String[]::new)));
  }

  void run(String[] args) throws IOException {
    runHarness(parseHarness(args));
  }

  private void runHarness(JazzerHarness harness) throws IOException {
    try (PrintWriter outputWriter = new TerminalPrintWriter(outputStream);
        PrintWriter errorWriter = new TerminalPrintWriter(errorStream)) {
      int exitCode = run(projectDirectory, harness, outputWriter, errorWriter);
      if (exitCode != 0) {
        exitHandler.exit(exitCode);
      }
    }
  }

  /**
   * Parses the required {@code --target <harness-key>} argument pair for direct regression replay.
   */
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
      Path projectDirectory,
      JazzerHarness harness,
      PrintWriter outputWriter,
      PrintWriter errorWriter)
      throws IOException {
    return run(projectDirectory, harness, outputWriter, errorWriter, JazzerReplayRunner::replay);
  }

  static int run(
      Path projectDirectory,
      JazzerHarness harness,
      PrintWriter outputWriter,
      PrintWriter errorWriter,
      ReplayExecutor replayExecutor)
      throws IOException {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    Objects.requireNonNull(harness, "harness must not be null");
    Objects.requireNonNull(outputWriter, "outputWriter must not be null");
    Objects.requireNonNull(errorWriter, "errorWriter must not be null");
    Objects.requireNonNull(replayExecutor, "replayExecutor must not be null");

    Path canonicalProjectDirectory =
        RegressionSeedRepositoryPathAdmission.canonicalProjectDirectory(projectDirectory);
    List<Path> metadataPaths =
        RegressionSeedPaths.metadataPaths(canonicalProjectDirectory, harness);
    if (metadataPaths.isEmpty()) {
      errorWriter.println(
          "No regression metadata entries were found for harness: " + harness.key());
      return 1;
    }

    outputWriter.println(
        PULSE_PREFIX
            + "regression-target event=plan target="
            + harness.key()
            + " total-inputs="
            + metadataPaths.size());

    for (int index = 0; index < metadataPaths.size(); index++) {
      Path metadataPath = metadataPaths.get(index);
      RegressionSeedMetadataInspection inspection =
          RegressionSeedMetadataInspector.inspectMetadataPath(
              canonicalProjectDirectory, harness, metadataPath);
      if (inspection.problem() != null) {
        writeIntegrityProblem(inspection.problem(), errorWriter);
        return 1;
      }
      RegressionSeedCatalogEntry entry =
          Objects.requireNonNull(inspection.entry(), "valid inspection entry must not be null");
      Path inputPath = entry.inputPath();
      ReplayOutcome outcome = replayExecutor.replay(harness, Files.readAllBytes(inputPath));
      ReplayExpectation actualExpectation = JazzerReplayRunner.expectationFor(outcome);
      if (!entry.expectation().equals(actualExpectation)) {
        errorWriter.println(
            "Regression mismatch for "
                + harness.key()
                + " input "
                + inputPath.getFileName()
                + ": expected "
                + JazzerJson.toJson(entry.expectation())
                + " but got "
                + JazzerJson.toJson(actualExpectation));
        writeUnexpectedFailureStackTrace(outcome, errorWriter);
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
        PULSE_PREFIX
            + "regression-target event=finish target="
            + harness.key()
            + " status=SUCCESS");
    return 0;
  }

  private static void writeIntegrityProblem(
      RegressionSeedIntegrityProblem problem, PrintWriter errorWriter) {
    switch (problem.problemKind()) {
      case "target-mismatch" ->
          errorWriter.println("Regression metadata target mismatch: " + problem.message());
      case "input-missing" ->
          errorWriter.println("Committed regression input does not exist: " + problem.inputPath());
      default -> errorWriter.println(problem.message());
    }
  }

  private static void writeUnexpectedFailureStackTrace(
      ReplayOutcome outcome, PrintWriter errorWriter) {
    if (outcome instanceof ReplayOutcome.UnexpectedFailure unexpectedFailure) {
      errorWriter.println(unexpectedFailure.stackTrace());
    }
  }

  /** Terminates the process with one computed regression exit code. */
  @FunctionalInterface
  interface ExitHandler {
    /** Exits the current process with the supplied regression status code. */
    void exit(int exitCode);
  }

  /** Replays one harness/input pair for regression verification. */
  @FunctionalInterface
  interface ReplayExecutor {
    /** Replays one harness/input pair and returns the resulting replay outcome. */
    ReplayOutcome replay(JazzerHarness harness, byte[] input);
  }

  record MainArguments(Path projectDirectory, List<String> commandArguments) {
    static MainArguments parse(String[] args) {
      Objects.requireNonNull(args, "args must not be null");
      requireProjectRootInvocation(args);
      String projectRoot = Objects.requireNonNull(args[1], "projectRoot must not be null");
      if (projectRoot.isBlank()) {
        throw new IllegalArgumentException(PROJECT_ROOT_OPTION + " must not be blank");
      }
      return new MainArguments(
          Path.of(projectRoot).toAbsolutePath().normalize(),
          List.of(java.util.Arrays.copyOfRange(args, 2, args.length)));
    }

    private static void requireProjectRootInvocation(String[] args) {
      if (args.length < 4) {
        throw usageException();
      }
      if (!PROJECT_ROOT_OPTION.equals(args[0])) {
        throw usageException();
      }
    }

    private static IllegalArgumentException usageException() {
      return new IllegalArgumentException(
          "Usage: JazzerRegressionRunner "
              + PROJECT_ROOT_OPTION
              + " <jazzer-project-dir> --target <harness-key>");
    }
  }
}
