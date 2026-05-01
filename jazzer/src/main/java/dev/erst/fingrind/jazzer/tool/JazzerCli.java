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
import org.jspecify.annotations.Nullable;

/** Implements the supported local Jazzer operator commands beyond active fuzz launchers. */
public final class JazzerCli {
  private static final String PROJECT_ROOT_OPTION = "--project-root";
  private static final Path UNUSED_PROJECT_DIRECTORY =
      Path.of(System.getProperty("java.io.tmpdir")).resolve("fingrind-unused-jazzer-root");
  private final Path projectDirectory;
  private final OutputStream outputStream;
  private final OutputStream errorStream;
  private final ExitHandler exitHandler;

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
    MainArguments mainArguments = MainArguments.parse(arguments);
    new JazzerCli(mainArguments.projectDirectory(), System.out, System.err, System::exit)
        .run(mainArguments.commandArguments().toArray(String[]::new));
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
        case ACTIVE_TARGET_KEYS ->
            printActiveTargetKeys(args.subList(1, args.size()), outputWriter);
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
    byte[] inputBytes;
    try {
      inputBytes = Files.readAllBytes(replayArguments.inputPath());
    } catch (IOException exception) {
      throw usageError(
          "Failed to read replay input path: "
              + replayArguments.inputPath()
              + " ("
              + exception.getMessage()
              + ")",
          Command.REPLAY,
          exception);
    }
    ReplayOutcome outcome =
        JazzerReplayRunner.replay(replayArguments.target().replayHarness(), inputBytes);
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
          renderFindingListing(targets.get(index).key(), findingsByTarget.get(index)));
    }
    return 0;
  }

  private static int printActiveTargetKeys(List<String> args, PrintWriter outputWriter) {
    if (!args.isEmpty()) {
      throw usageError(
          "active-target-keys does not accept additional arguments.", Command.ACTIVE_TARGET_KEYS);
    }
    Arrays.stream(JazzerRunTarget.values())
        .filter(JazzerRunTarget::activeFuzzing)
        .map(JazzerRunTarget::key)
        .forEach(outputWriter::println);
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

  static String renderFindingListing(String targetKey, List<FindingArtifact> findings) {
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
        "  JazzerCli "
            + PROJECT_ROOT_OPTION
            + " <jazzer-project-dir> "
            + Command.REPLAY.usageSynopsis(),
        "  JazzerCli "
            + PROJECT_ROOT_OPTION
            + " <jazzer-project-dir> "
            + Command.LIST_FINDINGS.usageSynopsis(),
        "  JazzerCli " + Command.ACTIVE_TARGET_KEYS.usageSynopsis(),
        "  JazzerCli --help",
        "",
        "Commands:",
        "  replay         Replay one raw local input against one replayable harness.",
        "  list-findings  Replay-classify raw local finding artifacts for one or all harnesses.",
        "  active-target-keys  Print the active fuzz target keys in canonical topology order.",
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
    REPLAY("replay <target-key> <input-path> [--json]"),
    LIST_FINDINGS("list-findings [<target-key>] [--json]"),
    ACTIVE_TARGET_KEYS("active-target-keys");

    private final String usageSynopsis;

    Command(String usageSynopsis) {
      this.usageSynopsis = usageSynopsis;
    }

    private static Command fromToken(String token) {
      return switch (Objects.requireNonNull(token, "token must not be null")) {
        case "replay" -> REPLAY;
        case "list-findings" -> LIST_FINDINGS;
        case "active-target-keys" -> ACTIVE_TARGET_KEYS;
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
      if (args.isEmpty() || args.getFirst().startsWith("-")) {
        throw usageError("Missing required target key.", Command.REPLAY);
      }
      String targetKey = args.getFirst();
      if (args.size() < 2 || args.get(1).startsWith("-")) {
        throw usageError("Missing required input path.", Command.REPLAY);
      }
      String inputPath = args.get(1);
      if (args.size() > 2 && !"--json".equals(args.get(2))) {
        throw usageError("Unexpected replay argument: " + args.get(2), Command.REPLAY);
      }
      if (args.size() > 3) {
        throw usageError("Unexpected replay argument: " + args.get(3), Command.REPLAY);
      }
      boolean jsonOutput = args.size() > 2;
      JazzerRunTarget target = JazzerRunTarget.fromKey(targetKey);
      if (!target.replayable()) {
        throw usageError(
            "Replay requires a single-harness target, not " + target.key(), Command.REPLAY);
      }
      Path normalizedInputPath = Path.of(inputPath).toAbsolutePath().normalize();
      if (!Files.exists(normalizedInputPath)) {
        throw usageError(
            "Replay input path does not exist: " + normalizedInputPath, Command.REPLAY);
      }
      if (!Files.isRegularFile(normalizedInputPath)) {
        throw usageError(
            "Replay input path must be a regular file: " + normalizedInputPath, Command.REPLAY);
      }
      return new ReplayCommandArguments(target, normalizedInputPath, jsonOutput);
    }
  }

  private record ListFindingsCommandArguments(@Nullable String targetKey, boolean jsonOutput) {
    private static ListFindingsCommandArguments parse(List<String> args) {
      String targetKey = null;
      int index = 0;
      if (!args.isEmpty() && !args.getFirst().startsWith("-")) {
        targetKey = args.getFirst();
        index = 1;
      }
      boolean jsonOutput = false;
      if (index < args.size()) {
        String argument = args.get(index);
        if ("--json".equals(argument)) {
          jsonOutput = true;
          index++;
        } else {
          throw usageError("Unexpected list-findings argument: " + argument, Command.LIST_FINDINGS);
        }
      }
      if (index < args.size()) {
        throw usageError(
            "Unexpected list-findings argument: " + args.get(index), Command.LIST_FINDINGS);
      }
      return new ListFindingsCommandArguments(targetKey, jsonOutput);
    }
  }

  record MainArguments(Path projectDirectory, List<String> commandArguments) {
    static MainArguments parse(String[] arguments) {
      Objects.requireNonNull(arguments, "arguments must not be null");
      if (allowsProjectRootlessInvocation(arguments)) {
        return new MainArguments(UNUSED_PROJECT_DIRECTORY, List.of(arguments.clone()));
      }
      requireProjectRootInvocation(arguments);
      String projectRoot = arguments[1];
      if (projectRoot.isBlank()) {
        throw new IllegalArgumentException(PROJECT_ROOT_OPTION + " must not be blank");
      }
      return new MainArguments(
          Path.of(projectRoot).toAbsolutePath().normalize(),
          List.of(Arrays.copyOfRange(arguments, 2, arguments.length)));
    }

    private static boolean allowsProjectRootlessInvocation(String[] arguments) {
      if (arguments.length == 0) {
        return true;
      }
      String firstArgument = arguments[0];
      return (arguments.length == 1 && "--help".equals(firstArgument))
          || "active-target-keys".equals(firstArgument);
    }

    private static void requireProjectRootInvocation(String[] arguments) {
      if (arguments.length < 3) {
        throw usageException();
      }
      if (!PROJECT_ROOT_OPTION.equals(arguments[0])) {
        throw usageException();
      }
    }

    private static IllegalArgumentException usageException() {
      return new IllegalArgumentException(
          "Usage: JazzerCli "
              + PROJECT_ROOT_OPTION
              + " <jazzer-project-dir> <command> [command-options]");
    }
  }

  private static IllegalArgumentException usageError(String message, Command command) {
    return new IllegalArgumentException(message + System.lineSeparator() + command.usage());
  }

  private static IllegalArgumentException usageError(
      String message, Command command, Throwable cause) {
    return new IllegalArgumentException(message + System.lineSeparator() + command.usage(), cause);
  }

  /** Terminates the process with one computed exit code. */
  @FunctionalInterface
  interface ExitHandler {
    /** Exits the current process with the supplied command status code. */
    void exit(int exitCode);
  }
}
