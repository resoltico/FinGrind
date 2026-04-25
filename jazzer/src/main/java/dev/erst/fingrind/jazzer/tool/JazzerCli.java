package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.jazzer.support.JazzerRunTarget;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Implements the supported local Jazzer operator commands beyond active fuzz launchers. */
public final class JazzerCli {
  private final Path projectDirectory;
  private final OutputStream outputStream;
  private final OutputStream errorStream;
  private final ExitHandler exitHandler;

  /**
   * Creates the production Jazzer CLI entrypoint backed by process streams and {@code
   * System::exit}.
   */
  public JazzerCli() {
    this(Path.of("").toAbsolutePath().normalize(), System.out, System.err, System::exit);
  }

  JazzerCli(
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

  /** Dispatches one Jazzer operator command and exits non-zero on usage errors or bugs. */
  public static void main(String[] arguments) throws IOException {
    new JazzerCli().run(arguments);
  }

  void run(String[] arguments) throws IOException {
    try (PrintWriter outputWriter = new TerminalPrintWriter(outputStream);
        PrintWriter errorWriter = new TerminalPrintWriter(errorStream)) {
      int exitCode = run(projectDirectory, arguments, outputWriter, errorWriter);
      if (exitCode != 0) {
        exitHandler.exit(exitCode);
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

    if (arguments.length == 0) {
      errorWriter.println("A Jazzer subcommand is required.");
      errorWriter.println();
      errorWriter.print(usageText());
      return 1;
    }

    if (arguments.length == 1 && "--help".equals(arguments[0])) {
      outputWriter.print(usageText());
      return 0;
    }

    List<String> args = Arrays.asList(arguments);
    Command command;
    try {
      command = Command.fromToken(args.getFirst());
    } catch (IllegalArgumentException exception) {
      errorWriter.println(exception.getMessage());
      errorWriter.println();
      errorWriter.print(usageText());
      return 1;
    }

    try {
      return switch (command) {
        case REPLAY -> replay(args.subList(1, args.size()), outputWriter);
        case LIST_FINDINGS ->
            listFindings(projectDirectory, args.subList(1, args.size()), outputWriter);
      };
    } catch (IllegalArgumentException exception) {
      errorWriter.println(exception.getMessage());
      errorWriter.println();
      errorWriter.print(command.usage());
      return 1;
    }
  }

  private static int replay(List<String> args, PrintWriter outputWriter) throws IOException {
    ReplayCommandArguments replayArguments = ReplayCommandArguments.parse(args);
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(
            replayArguments.target().replayHarness(),
            Files.readAllBytes(replayArguments.inputPath()));
    if (replayArguments.jsonOutput()) {
      outputWriter.println(JazzerJson.toJson(outcome));
    } else {
      outputWriter.println(renderReplay(replayArguments.inputPath(), outcome));
    }
    return replayExitCode(outcome);
  }

  static int replayExitCode(ReplayOutcome outcome) {
    return outcome instanceof ReplayOutcome.UnexpectedFailure ? 1 : 0;
  }

  private static int listFindings(
      Path projectDirectory, List<String> args, PrintWriter outputWriter) throws IOException {
    ListFindingsCommandArguments listFindingsArguments = ListFindingsCommandArguments.parse(args);
    List<JazzerRunTarget> targets =
        listFindingsArguments.targetKey() == null
            ? Arrays.stream(JazzerRunTarget.values())
                .filter(JazzerRunTarget::activeFuzzing)
                .toList()
            : List.of(JazzerRunTarget.fromKey(listFindingsArguments.targetKey()));

    List<FindingArtifact> findings = new java.util.ArrayList<>();
    for (JazzerRunTarget target : targets) {
      findings.addAll(JazzerFindingSupport.findingArtifacts(projectDirectory, target));
    }

    if (listFindingsArguments.jsonOutput()) {
      outputWriter.println(JazzerJson.toJson(findings));
      return 0;
    }

    for (int index = 0; index < targets.size(); index++) {
      JazzerRunTarget target = targets.get(index);
      if (index > 0) {
        outputWriter.println();
      }
      outputWriter.println(
          renderFindingListing(
              target.key(), JazzerFindingSupport.findingArtifacts(projectDirectory, target)));
    }
    return 0;
  }

  private static String renderReplay(Path inputPath, ReplayOutcome outcome) throws IOException {
    return String.join(
        System.lineSeparator(),
        "Input: " + inputPath,
        "Harness: " + outcome.harnessKey(),
        "Outcome: " + outcome.kind().wireValue(),
        "Message: " + outcome.message(),
        "Details:",
        JazzerJson.toJson(outcome.details()));
  }

  private static String renderFindingListing(String targetKey, List<FindingArtifact> findings) {
    long unexpectedFailures =
        findings.stream()
            .filter(
                finding ->
                    finding.replayClassification()
                        == ReplayFindingClassification.UNEXPECTED_FAILURE)
            .count();
    long expectedInvalid =
        findings.stream()
            .filter(
                finding ->
                    finding.replayClassification() == ReplayFindingClassification.EXPECTED_INVALID)
            .count();
    long replayClean =
        findings.stream()
            .filter(
                finding ->
                    finding.replayClassification() == ReplayFindingClassification.REPLAY_CLEAN)
            .count();

    StringBuilder builder =
        new StringBuilder(256)
            .append("Target: ")
            .append(targetKey)
            .append(System.lineSeparator())
            .append("Summary: actionable=")
            .append(unexpectedFailures)
            .append(" expected-invalid=")
            .append(expectedInvalid)
            .append(" replay-clean=")
            .append(replayClean);
    if (findings.isEmpty()) {
      builder
          .append(System.lineSeparator())
          .append("No raw libFuzzer artifacts are currently recorded for this target.");
      return builder.toString();
    }

    for (FindingArtifact finding : findings) {
      builder
          .append(System.lineSeparator())
          .append(System.lineSeparator())
          .append(finding.rawArtifactName())
          .append(" | ")
          .append(finding.replayClassification().wireValue())
          .append(" | ")
          .append(finding.message())
          .append(System.lineSeparator())
          .append("Path: ")
          .append(finding.rawArtifactPath());
    }
    return builder.toString();
  }

  private static String usageText() {
    return String.join(
        System.lineSeparator(),
        "Usage:",
        "  JazzerCli " + Command.REPLAY.usageSynopsis(),
        "  JazzerCli " + Command.LIST_FINDINGS.usageSynopsis(),
        "  JazzerCli --help",
        "",
        "Commands:",
        "  replay         Replay one raw local input against one replayable harness.",
        "  list-findings  Replay-classify raw local finding artifacts for one or all harnesses.",
        "",
        "Replayable targets:",
        "  " + supportedReplayTargets());
  }

  private static String supportedReplayTargets() {
    return Arrays.stream(JazzerRunTarget.values())
        .filter(JazzerRunTarget::replayable)
        .map(JazzerRunTarget::key)
        .sorted()
        .reduce((left, right) -> left + ", " + right)
        .orElse("(none)");
  }

  /** Supported top-level Jazzer operator commands exposed by the local CLI wrapper. */
  private enum Command {
    REPLAY("replay --target <target-key> --input <input-path> [--json]"),
    LIST_FINDINGS("list-findings [--target <target-key>] [--json]");

    private final String usageSynopsis;

    Command(String usageSynopsis) {
      this.usageSynopsis = usageSynopsis;
    }

    private static Command fromToken(String token) {
      return switch (Objects.requireNonNull(token, "token must not be null")) {
        case "replay" -> REPLAY;
        case "list-findings" -> LIST_FINDINGS;
        default -> throw new IllegalArgumentException("Unknown Jazzer subcommand: " + token);
      };
    }

    private String usageSynopsis() {
      return usageSynopsis;
    }

    private String usage() {
      return "Usage: JazzerCli " + usageSynopsis;
    }
  }

  private record ReplayCommandArguments(
      JazzerRunTarget target, Path inputPath, boolean jsonOutput) {
    private ReplayCommandArguments {
      Objects.requireNonNull(target, "target must not be null");
      Objects.requireNonNull(inputPath, "inputPath must not be null");
    }

    private static ReplayCommandArguments parse(List<String> args) {
      String targetKey = null;
      String inputPath = null;
      boolean jsonOutput = false;
      Set<String> seenFlags = new LinkedHashSet<>();
      int index = 0;
      while (index < args.size()) {
        String argument = args.get(index);
        switch (argument) {
          case "--target" -> {
            requireUniqueFlag(seenFlags, argument, Command.REPLAY);
            targetKey = requireNextValue(args, index, argument, Command.REPLAY);
            index += 2;
          }
          case "--input" -> {
            requireUniqueFlag(seenFlags, argument, Command.REPLAY);
            inputPath = requireNextValue(args, index, argument, Command.REPLAY);
            index += 2;
          }
          case "--json" -> {
            requireUniqueFlag(seenFlags, argument, Command.REPLAY);
            jsonOutput = true;
            index++;
          }
          default -> throw usageError("Unexpected replay argument: " + argument, Command.REPLAY);
        }
      }
      if (targetKey == null) {
        throw usageError("Missing required option --target", Command.REPLAY);
      }
      if (inputPath == null) {
        throw usageError("Missing required option --input", Command.REPLAY);
      }
      JazzerRunTarget target = JazzerRunTarget.fromKey(targetKey);
      if (!target.replayable()) {
        throw usageError(
            "Replay requires a single-harness target, not " + target.key(), Command.REPLAY);
      }
      return new ReplayCommandArguments(
          target, Path.of(inputPath).toAbsolutePath().normalize(), jsonOutput);
    }
  }

  private record ListFindingsCommandArguments(@Nullable String targetKey, boolean jsonOutput) {
    private static ListFindingsCommandArguments parse(List<String> args) {
      String targetKey = null;
      boolean jsonOutput = false;
      Set<String> seenFlags = new LinkedHashSet<>();
      int index = 0;
      while (index < args.size()) {
        String argument = args.get(index);
        switch (argument) {
          case "--target" -> {
            requireUniqueFlag(seenFlags, argument, Command.LIST_FINDINGS);
            targetKey = requireNextValue(args, index, argument, Command.LIST_FINDINGS);
            index += 2;
          }
          case "--json" -> {
            requireUniqueFlag(seenFlags, argument, Command.LIST_FINDINGS);
            jsonOutput = true;
            index++;
          }
          default ->
              throw usageError(
                  "Unexpected list-findings argument: " + argument, Command.LIST_FINDINGS);
        }
      }
      return new ListFindingsCommandArguments(targetKey, jsonOutput);
    }
  }

  private static void requireUniqueFlag(Set<String> seenFlags, String flag, Command command) {
    if (!seenFlags.add(flag)) {
      throw usageError("Duplicate option " + flag, command);
    }
  }

  private static String requireNextValue(
      List<String> args, int flagIndex, String flag, Command command) {
    if (flagIndex + 1 >= args.size()) {
      throw usageError("Missing value after " + flag, command);
    }
    return args.get(flagIndex + 1);
  }

  private static IllegalArgumentException usageError(String message, Command command) {
    return new IllegalArgumentException(message + System.lineSeparator() + command.usage());
  }

  /** Terminates the process with one computed exit code. */
  @FunctionalInterface
  interface ExitHandler {
    /** Exits the current process with the supplied command status code. */
    void exit(int exitCode);
  }
}
