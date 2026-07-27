package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerRunTarget;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/** Implements the supported local Jazzer operator commands beyond active fuzz launchers. */
public final class JazzerCli {
  private final Path projectDirectory;
  private final OutputStream outputStream;
  private final OutputStream errorStream;
  private final IntConsumer exitHandler;

  JazzerCli(
      Path projectDirectory,
      OutputStream outputStream,
      OutputStream errorStream,
      IntConsumer exitHandler) {
    this.projectDirectory =
        Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    this.outputStream = Objects.requireNonNull(outputStream, "outputStream must not be null");
    this.errorStream = Objects.requireNonNull(errorStream, "errorStream must not be null");
    this.exitHandler = Objects.requireNonNull(exitHandler, "exitHandler must not be null");
  }

  /** Dispatches one Jazzer operator command and exits non-zero on usage errors or bugs. */
  public static void main(String[] arguments) throws IOException {
    JazzerCliMainArguments mainArguments = JazzerCliMainArguments.parse(arguments);
    new JazzerCli(mainArguments.projectDirectory(), System.out, System.err, System::exit)
        .run(mainArguments.commandArguments().toArray(String[]::new));
  }

  void run(String[] arguments) throws IOException {
    try (PrintWriter outputWriter = new TerminalPrintWriter(outputStream);
        PrintWriter errorWriter = new TerminalPrintWriter(errorStream)) {
      int exitCode = run(projectDirectory, arguments, outputWriter, errorWriter);
      JazzerCliWrapperExitStatus.write(exitCode);
      if (exitCode != 0 && !JazzerCliWrapperExitStatus.managed()) {
        exitHandler.accept(exitCode);
      }
    }
  }

  static int run(
      Path projectDirectory, String[] arguments, PrintWriter outputWriter, PrintWriter errorWriter)
      throws IOException {
    Objects.requireNonNull(projectDirectory, "projectDirectory must not be null");
    Objects.requireNonNull(arguments, "arguments must not be null");
    Objects.requireNonNull(outputWriter, "outputWriter must not be null");
    Objects.requireNonNull(errorWriter, "errorWriter must not be null");
    List<String> args = Arrays.asList(arguments);
    boolean jsonOutputRequested = args.contains("--json");

    if (arguments.length == 0) {
      JazzerCliFailureWriter.writeFailure(
          outputWriter,
          errorWriter,
          jsonOutputRequested,
          new JazzerCliCommandFailurePayload(
              "error",
              null,
              1,
              "A Jazzer subcommand is required.",
              List.of(),
              JazzerCliUsageText.usageText()));
      return 1;
    }

    if (arguments.length == 1 && "--help".equals(arguments[0])) {
      outputWriter.print(JazzerCliUsageText.usageText());
      return 0;
    }

    JazzerCliCommand command;
    try {
      command = JazzerCliCommand.fromToken(args.getFirst());
    } catch (IllegalArgumentException exception) {
      JazzerCliFailureWriter.writeFailure(
          outputWriter,
          errorWriter,
          jsonOutputRequested,
          new JazzerCliCommandFailurePayload(
              "error",
              args.getFirst(),
              1,
              JazzerCliFailureWriter.failureMessage(exception),
              List.of(),
              JazzerCliUsageText.usageText()));
      return 1;
    }

    try {
      return switch (command) {
        case REPLAY -> replay(args.subList(1, args.size()), outputWriter);
        case PROMOTE_SEED ->
            promoteSeed(projectDirectory, args.subList(1, args.size()), outputWriter);
        case LIST_FINDINGS ->
            listFindings(projectDirectory, args.subList(1, args.size()), outputWriter);
        case SEED_AUDIT -> seedAudit(projectDirectory, args.subList(1, args.size()), outputWriter);
        case ACTIVE_TARGET_KEYS ->
            printActiveTargetKeys(args.subList(1, args.size()), outputWriter);
      };
    } catch (RegressionSeedPromotionRetainedArtifactsException exception) {
      JazzerCliFailureWriter.writeFailure(
          outputWriter,
          errorWriter,
          jsonOutputRequested,
          new JazzerCliCommandFailurePayload(
              "error",
              command.token(),
              1,
              JazzerCliFailureWriter.failureMessage(exception),
              exception.retention().retainedArtifactPaths().stream()
                  .map(Path::toString)
                  .toList(),
              command.usage()));
      return 1;
    } catch (IllegalArgumentException exception) {
      JazzerCliFailureWriter.writeFailure(
          outputWriter,
          errorWriter,
          jsonOutputRequested,
          new JazzerCliCommandFailurePayload(
              "error",
              command.token(),
              1,
              JazzerCliFailureWriter.failureMessage(exception),
              List.of(),
              command.usage()));
      return 1;
    }
  }

  private static int replay(List<String> args, PrintWriter outputWriter) throws IOException {
    JazzerCliReplayCommandArguments replayArguments = JazzerCliReplayCommandArguments.parse(args);
    byte[] inputBytes;
    try {
      inputBytes = Files.readAllBytes(replayArguments.inputPath());
    } catch (IOException exception) {
      throw new IllegalArgumentException(
          "Failed to read replay input path: "
              + replayArguments.inputPath()
              + " ("
              + exception.getMessage()
              + ")",
          exception);
    }
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(replayArguments.target().replayHarness(), inputBytes);
    if (replayArguments.jsonOutput()) {
      outputWriter.println(JazzerJson.toJson(outcome));
    } else {
      outputWriter.println(JazzerReplayTextRenderer.render(replayArguments.inputPath(), outcome));
    }
    return replayExitCode(outcome);
  }

  static int replayExitCode(ReplayOutcome outcome) {
    return outcome instanceof ReplayOutcome.UnexpectedFailure ? 1 : 0;
  }

  private static int listFindings(
      Path projectDirectory, List<String> args, PrintWriter outputWriter) throws IOException {
    JazzerCliListFindingsCommandArguments listFindingsArguments =
        JazzerCliListFindingsCommandArguments.parse(args);
    List<JazzerRunTarget> targets =
        listFindingsArguments.targetKey() == null
            ? Arrays.stream(JazzerRunTarget.values())
                .filter(JazzerRunTarget::activeFuzzing)
                .toList()
            : List.of(JazzerRunTarget.fromKey(listFindingsArguments.targetKey()));

    List<List<FindingArtifact>> findingsByTarget = new ArrayList<>(targets.size());
    for (JazzerRunTarget target : targets) {
      findingsByTarget.add(JazzerFindingSupport.findingArtifacts(projectDirectory, target));
    }

    if (listFindingsArguments.jsonOutput()) {
      outputWriter.println(
          JazzerJson.toJson(findingsByTarget.stream().flatMap(List::stream).toList()));
      return 0;
    }

    for (int index = 0; index < targets.size(); index++) {
      if (index > 0) {
        outputWriter.println();
      }
      outputWriter.println(
          JazzerFindingListingTextRenderer.render(
              targets.get(index).key(), findingsByTarget.get(index)));
    }
    return 0;
  }

  private static int promoteSeed(Path projectDirectory, List<String> args, PrintWriter outputWriter)
      throws IOException {
    JazzerCliPromoteSeedCommandArguments promoteSeedArguments =
        JazzerCliPromoteSeedCommandArguments.parse(args);
    RegressionSeedPromotionResult result =
        RegressionSeedPromoter.promote(
            projectDirectory,
            promoteSeedArguments.target().replayHarness(),
            promoteSeedArguments.inputPath(),
            promoteSeedArguments.seedName(),
            promoteSeedArguments.coverageIntent());
    if (promoteSeedArguments.jsonOutput()) {
      outputWriter.println(JazzerJson.toJson(result));
    } else {
      outputWriter.println(JazzerPromotionTextRenderer.render(result));
    }
    return 0;
  }

  private static int seedAudit(Path projectDirectory, List<String> args, PrintWriter outputWriter)
      throws IOException {
    JazzerCliSeedAuditCommandArguments seedAuditArguments =
        JazzerCliSeedAuditCommandArguments.parse(args);
    RegressionSeedAuditReport report =
        seedAuditArguments.targetKey() == null
            ? RegressionSeedAuditor.audit(projectDirectory)
            : RegressionSeedAuditor.audit(
                projectDirectory,
                JazzerRunTarget.fromKey(seedAuditArguments.targetKey()).replayHarness());
    if (seedAuditArguments.jsonOutput()) {
      outputWriter.println(JazzerJson.toJson(report));
    } else {
      outputWriter.println(JazzerSeedAuditTextRenderer.render(report));
    }
    return report.duplicateContentGroups().isEmpty()
            && report.orphanedInputPaths().isEmpty()
            && report.unexpectedFailureSeeds().isEmpty()
            && report.integrityProblems().isEmpty()
        ? 0
        : 1;
  }

  private static int printActiveTargetKeys(List<String> args, PrintWriter outputWriter) {
    if (!args.isEmpty()) {
      throw new IllegalArgumentException(
          "active-target-keys does not accept additional arguments.");
    }
    Arrays.stream(JazzerRunTarget.values())
        .filter(JazzerRunTarget::activeFuzzing)
        .map(JazzerRunTarget::key)
        .forEach(outputWriter::println);
    return 0;
  }
}
