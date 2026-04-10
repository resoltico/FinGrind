package dev.erst.fingrind.cli;

import dev.erst.fingrind.application.PostEntryCommand;
import dev.erst.fingrind.application.PostEntryResult;
import dev.erst.fingrind.application.PostingApplicationService;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.sqlite.SqlitePostingFactStore;
import dev.erst.fingrind.sqlite.SqliteRuntime;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Command dispatcher for the FinGrind agent-first CLI surface. */
final class FinGrindCli {
  private static final String HELP_HINT = "Run 'fingrind help' to inspect the supported commands.";

  private final CliRequestReader requestReader;
  private final CliResponseWriter responseWriter;
  private final CliMetadata metadata;
  private final Clock clock;
  private final EntryWorkflow entryWorkflow;

  FinGrindCli(InputStream inputStream, PrintStream outputStream, Clock clock) {
    this(inputStream, outputStream, clock, new SqliteEntryWorkflow(clock));
  }

  FinGrindCli(
      InputStream inputStream, PrintStream outputStream, Clock clock, EntryWorkflow entryWorkflow) {
    this.requestReader = new CliRequestReader(inputStream);
    this.responseWriter = new CliResponseWriter(outputStream);
    this.metadata = new CliMetadata();
    this.clock = Objects.requireNonNull(clock, "clock");
    this.entryWorkflow = Objects.requireNonNull(entryWorkflow, "entryWorkflow");
  }

  /** Runs one CLI command and writes a deterministic JSON envelope. */
  int run(String[] args) {
    try {
      return switch (CliArguments.parse(args)) {
        case CliCommand.Help _ -> writeDiscoverySuccess(helpPayload());
        case CliCommand.Capabilities _ -> writeDiscoverySuccess(capabilitiesPayload());
        case CliCommand.Version _ -> writeDiscoverySuccess(versionPayload());
        case CliCommand.PrintRequestTemplate _ -> writeRequestTemplate();
        case CliCommand.PreflightEntry command ->
            runEntryCommand(
                command.bookFilePath(), command.requestFile(), entryWorkflow::preflight);
        case CliCommand.PostEntry command ->
            runEntryCommand(command.bookFilePath(), command.requestFile(), entryWorkflow::commit);
      };
    } catch (CliArgumentsException exception) {
      responseWriter.writeFailure(exception.failure());
      return 2;
    } catch (CliRequestException exception) {
      responseWriter.writeFailure(exception.failure());
      return 2;
    } catch (IllegalArgumentException exception) {
      responseWriter.writeFailure(
          new CliFailure("invalid-request", message(exception), HELP_HINT, null));
      return 2;
    } catch (IllegalStateException exception) {
      responseWriter.writeFailure(runtimeFailure(exception));
      return 1;
    }
  }

  private int writeDiscoverySuccess(Map<String, ?> payload) {
    responseWriter.writeSuccess(payload, true);
    return 0;
  }

  private int writeRequestTemplate() {
    responseWriter.writeJson(requestTemplatePayload(), true);
    return 0;
  }

  private int runEntryCommand(Path bookFilePath, Path requestFile, EntryOperation entryOperation) {
    PostEntryCommand command = requestReader.readPostEntryCommand(requestFile);
    PostEntryResult result = entryOperation.execute(bookFilePath, command);
    responseWriter.writePostEntryResult(result);
    return exitCodeFor(result);
  }

  private static int exitCodeFor(PostEntryResult result) {
    return switch (result) {
      case PostEntryResult.PreflightAccepted _ -> 0;
      case PostEntryResult.Committed _ -> 0;
      case PostEntryResult.Rejected _ -> 2;
    };
  }

  @SuppressWarnings("PMD.UseConcurrentHashMap")
  private Map<String, Object> helpPayload() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("application", metadata.applicationName());
    payload.put("version", metadata.version());
    payload.put("description", metadata.description());
    payload.put(
        "usage",
        List.of(
            "fingrind help",
            "fingrind version",
            "fingrind capabilities",
            "fingrind print-request-template",
            "fingrind preflight-entry --book-file <path> --request-file <path|->",
            "fingrind post-entry --book-file <path> --request-file <path|->"));
    payload.put("bookModel", bookModelPayload());
    payload.put("commands", commandPayloads());
    payload.put(
        "quickStart",
        List.of(
            "fingrind print-request-template > request.json",
            "fingrind preflight-entry --book-file ./books/acme.sqlite --request-file request.json",
            "fingrind post-entry --book-file ./books/acme.sqlite --request-file request.json"));
    payload.put("exitCodes", exitCodePayload());
    payload.put("environment", environmentPayload());
    return payload;
  }

  @SuppressWarnings("PMD.UseConcurrentHashMap")
  private Map<String, Object> capabilitiesPayload() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("application", metadata.applicationName());
    payload.put("version", metadata.version());
    payload.put("storage", List.of("sqlite"));
    payload.put("bookBoundary", "single-sqlite-file");
    payload.put(
        "discoveryCommands", List.of("help", "version", "capabilities", "print-request-template"));
    payload.put("writeCommands", List.of("preflight-entry", "post-entry"));
    payload.put("requestInput", requestInputPayload());
    payload.put("requestShape", requestShapePayload());
    payload.put("responseModel", responseModelPayload());
    payload.put(
        "audit",
        Map.of(
            "requestProvenanceFields",
            List.of(
                "actorId",
                "actorType",
                "commandId",
                "idempotencyKey",
                "causationId",
                "correlationId",
                "reason"),
            "committedFields",
            List.of("recordedAt", "sourceChannel")));
    payload.put(
        "reversals",
        Map.of(
            "requirements",
            List.of(
                "target-must-exist-in-book",
                "reason-required",
                "reason-forbidden-without-reversal",
                "one-reversal-per-target",
                "reversal-must-negate-target")));
    payload.put("environment", environmentPayload());
    payload.put("timestamp", Instant.now(clock).toString());
    return payload;
  }

  @SuppressWarnings("PMD.UseConcurrentHashMap")
  private Map<String, Object> versionPayload() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("application", metadata.applicationName());
    payload.put("version", metadata.version());
    payload.put("description", metadata.description());
    return payload;
  }

  @SuppressWarnings("PMD.UseConcurrentHashMap")
  private static Map<String, Object> requestTemplatePayload() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("effectiveDate", "2026-04-08");
    payload.put("lines", List.of(lineTemplate("1000", "DEBIT"), lineTemplate("2000", "CREDIT")));
    payload.put("provenance", provenanceTemplate());
    return payload;
  }

  @SuppressWarnings("PMD.UseConcurrentHashMap")
  private static Map<String, Object> lineTemplate(String accountCode, String side) {
    Map<String, Object> line = new LinkedHashMap<>();
    line.put("accountCode", accountCode);
    line.put("side", side);
    line.put("currencyCode", "EUR");
    line.put("amount", "10.00");
    return line;
  }

  @SuppressWarnings("PMD.UseConcurrentHashMap")
  private static Map<String, Object> provenanceTemplate() {
    Map<String, Object> provenance = new LinkedHashMap<>();
    provenance.put("actorId", "operator-1");
    provenance.put("actorType", "USER");
    provenance.put("commandId", "command-1");
    provenance.put("idempotencyKey", "idem-1");
    provenance.put("causationId", "cause-1");
    return provenance;
  }

  @SuppressWarnings("PMD.UseConcurrentHashMap")
  private static Map<String, Object> bookModelPayload() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("boundary", "one SQLite file equals one book");
    payload.put("entityScope", "one book belongs to one entity");
    payload.put("filesystem", "--book-file may point anywhere on the OS filesystem");
    return payload;
  }

  private static List<Map<String, Object>> commandPayloads() {
    return List.of(
        commandPayload(
            "help",
            List.of("--help", "-h"),
            List.of(),
            "json-envelope",
            "Print command usage, examples, and workflow guidance."),
        commandPayload(
            "version",
            List.of("--version"),
            List.of(),
            "json-envelope",
            "Print application identity, version, and description."),
        commandPayload(
            "capabilities",
            List.of(),
            List.of(),
            "json-envelope",
            "Print machine-readable command, request, and response capabilities."),
        commandPayload(
            "print-request-template",
            List.of("--print-request-template"),
            List.of(),
            "raw-json",
            "Print a minimal valid posting request JSON document."),
        commandPayload(
            "preflight-entry",
            List.of(),
            List.of("--book-file <path>", "--request-file <path|->"),
            "json-envelope",
            "Validate one posting request without committing it."),
        commandPayload(
            "post-entry",
            List.of(),
            List.of("--book-file <path>", "--request-file <path|->"),
            "json-envelope",
            "Commit one posting request into the selected SQLite book."));
  }

  @SuppressWarnings("PMD.UseConcurrentHashMap")
  private static Map<String, Object> commandPayload(
      String name, List<String> aliases, List<String> options, String output, String summary) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("name", name);
    if (!aliases.isEmpty()) {
      payload.put("aliases", aliases);
    }
    if (!options.isEmpty()) {
      payload.put("options", options);
    }
    payload.put("output", output);
    payload.put("summary", summary);
    return payload;
  }

  private static List<Map<String, Object>> exitCodePayload() {
    return List.of(
        exitCode(0, "successful command"),
        exitCode(2, "invalid request or deterministic rejection"),
        exitCode(1, "runtime or environment failure"));
  }

  @SuppressWarnings("PMD.UseConcurrentHashMap")
  private static Map<String, Object> exitCode(int code, String meaning) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("code", code);
    payload.put("meaning", meaning);
    return payload;
  }

  private static Map<String, Object> environmentPayload() {
    return environmentPayload(SqliteRuntime.probe());
  }

  @SuppressWarnings("PMD.UseConcurrentHashMap")
  static Map<String, Object> environmentPayload(SqliteRuntime.Probe runtimeProbe) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("packagedJarJava", "26+");
    payload.put("storageDriver", SqliteRuntime.STORAGE_DRIVER);
    payload.put("storageEngine", SqliteRuntime.STORAGE_ENGINE);
    payload.put("sqliteLibrarySource", runtimeProbe.librarySource());
    payload.put("requiredMinimumSqliteVersion", runtimeProbe.requiredMinimumSqliteVersion());
    payload.put("sqliteRuntimeStatus", runtimeProbe.status().wireValue());
    if (runtimeProbe.loadedSqliteVersion() != null) {
      payload.put("loadedSqliteVersion", runtimeProbe.loadedSqliteVersion());
    }
    if (runtimeProbe.issue() != null) {
      payload.put("sqliteRuntimeIssue", runtimeProbe.issue());
    }
    return payload;
  }

  @SuppressWarnings("PMD.UseConcurrentHashMap")
  private static Map<String, Object> requestInputPayload() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("bookFileOption", "--book-file");
    payload.put("requestFileOption", "--request-file");
    payload.put("stdinToken", "-");
    payload.put("bookFileSemantics", "single SQLite book file for one entity");
    return payload;
  }

  @SuppressWarnings("PMD.UseConcurrentHashMap")
  private static Map<String, Object> requestShapePayload() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("requiredTopLevelFields", List.of("effectiveDate", "lines", "provenance"));
    payload.put("optionalTopLevelFields", List.of("reversal"));
    payload.put("forbiddenTopLevelFields", List.of("correction"));
    payload.put("requiredLineFields", List.of("accountCode", "side", "currencyCode", "amount"));
    payload.put(
        "requiredProvenanceFields",
        List.of("actorId", "actorType", "commandId", "idempotencyKey", "causationId"));
    payload.put("optionalProvenanceFields", List.of("correlationId", "reason"));
    payload.put("forbiddenProvenanceFields", List.of("recordedAt", "sourceChannel"));
    payload.put("requiredReversalFields", List.of("priorPostingId"));
    payload.put("forbiddenReversalFields", List.of("kind"));
    payload.put("enums", enumPayload());
    return payload;
  }

  @SuppressWarnings("PMD.UseConcurrentHashMap")
  private static Map<String, Object> enumPayload() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("lineSide", List.of("DEBIT", "CREDIT"));
    payload.put("actorType", List.of("USER", "SYSTEM", "AGENT"));
    return payload;
  }

  @SuppressWarnings("PMD.UseConcurrentHashMap")
  private static Map<String, Object> responseModelPayload() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("successStatuses", List.of("ok", "preflight-accepted", "committed"));
    payload.put("rejectionStatus", "rejected");
    payload.put("errorStatus", "error");
    payload.put(
        "rejectionCodes",
        List.of(
            "duplicate-idempotency-key",
            "reversal-reason-required",
            "reversal-reason-forbidden",
            "reversal-target-not-found",
            "reversal-already-exists",
            "reversal-does-not-negate-target"));
    payload.put(
        "rejectionFields", List.of("status", "code", "message", "idempotencyKey", "details"));
    payload.put("errorFields", List.of("status", "code", "message", "hint", "argument"));
    return payload;
  }

  private CliFailure runtimeFailure(IllegalStateException exception) {
    String message = message(exception);
    String hint =
        message.contains("SQLite")
            ? "Inspect the selected book file path, filesystem permissions, and the SQLite runtime"
                + " message, then rerun after fixing the underlying storage problem."
            : "Inspect the message and rerun after fixing the underlying runtime problem.";
    return new CliFailure("runtime-failure", message, hint, null);
  }

  private static String message(Exception exception) {
    return Objects.requireNonNullElse(exception.getMessage(), "CLI command failed.");
  }

  /** Execution seam for routing CLI commands through the selected book adapter. */
  interface EntryWorkflow {
    /** Runs the preflight path for one CLI request against the selected book file. */
    PostEntryResult preflight(Path bookFilePath, PostEntryCommand command);

    /** Runs the commit path for one CLI request against the selected book file. */
    PostEntryResult commit(Path bookFilePath, PostEntryCommand command);
  }

  /** Default workflow that opens one SQLite-backed book file per command. */
  private static final class SqliteEntryWorkflow implements EntryWorkflow {
    private final Clock clock;

    private SqliteEntryWorkflow(Clock clock) {
      this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PostEntryResult preflight(Path bookFilePath, PostEntryCommand command) {
      try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookFilePath)) {
        return applicationService(postingFactStore, clock).preflight(command);
      }
    }

    @Override
    public PostEntryResult commit(Path bookFilePath, PostEntryCommand command) {
      try (SqlitePostingFactStore postingFactStore = new SqlitePostingFactStore(bookFilePath)) {
        return applicationService(postingFactStore, clock).commit(command);
      }
    }

    private static PostingApplicationService applicationService(
        SqlitePostingFactStore postingFactStore, Clock clock) {
      return new PostingApplicationService(
          postingFactStore, () -> new PostingId(UUID.randomUUID().toString()), clock);
    }
  }

  /** Executes one write-boundary command against the selected book file. */
  @FunctionalInterface
  private interface EntryOperation {
    /** Executes the selected write-boundary command. */
    PostEntryResult execute(Path bookFilePath, PostEntryCommand command);
  }
}
