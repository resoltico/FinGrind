package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.application.PostEntryCommand;
import dev.erst.fingrind.application.PostEntryResult;
import dev.erst.fingrind.application.PostingRejection;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link FinGrindCli}. */
class FinGrindCliTest {
  @TempDir Path tempDirectory;

  @Test
  void run_returnsHelpWhenArgumentsAreEmpty() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[0]);

    assertEquals(0, exitCode);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"usage\""));
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"print-request-template\""));
  }

  @Test
  void run_returnsCapabilities() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"capabilities"});

    assertEquals(0, exitCode);
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"requestProvenanceFields\""));
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"committedFields\""));
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"reversal-must-negate-target\""));
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"rejectionCodes\""));
  }

  @Test
  void run_returnsVersion() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"version"});

    assertEquals(0, exitCode);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"application\""));
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"version\""));
  }

  @Test
  void run_printsRequestTemplateWithoutCallerSuppliedCommitFields() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"print-request-template"});

    assertEquals(0, exitCode);
    String json = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(json.contains("\"effectiveDate\""));
    assertTrue(json.contains("\"provenance\""));
    assertFalse(json.contains("recordedAt"));
    assertFalse(json.contains("sourceChannel"));
  }

  @Test
  void run_preflightsEntryThroughDefaultSqliteWorkflowWithoutCreatingBook() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path bookFilePath = tempDirectory.resolve("live-books").resolve("entity.sqlite");
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode =
        cli.run(
            new String[] {
              "preflight-entry",
              "--book-file",
              bookFilePath.toString(),
              "--request-file",
              requestFile.toString()
            });

    assertEquals(0, exitCode);
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("\"status\":\"preflight-accepted\""));
    assertFalse(Files.exists(bookFilePath));
  }

  @Test
  void run_commitsEntryThroughDefaultSqliteWorkflow() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path bookFilePath = tempDirectory.resolve("committed-books").resolve("entity.sqlite");
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode =
        cli.run(
            new String[] {
              "post-entry",
              "--book-file",
              bookFilePath.toString(),
              "--request-file",
              requestFile.toString()
            });

    assertEquals(0, exitCode);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"status\":\"committed\""));
    assertTrue(Files.exists(bookFilePath));
  }

  @Test
  void run_preflightsEntryUsingSelectedBookFile() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path bookFilePath = tempDirectory.resolve("books").resolve("entity.sqlite");
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            new PostEntryResult.Committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z")));
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);

    int exitCode =
        cli.run(
            new String[] {
              "preflight-entry",
              "--book-file",
              bookFilePath.toString(),
              "--request-file",
              requestFile.toString()
            });

    assertEquals(0, exitCode);
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("\"status\":\"preflight-accepted\""));
    assertEquals(bookFilePath, workflow.preflightPaths().getFirst());
  }

  @Test
  void run_commitsEntryAndRejectsDuplicateCommit() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path bookFilePath = tempDirectory.resolve("db.sqlite");
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            new PostEntryResult.Committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z")),
            new PostEntryResult.Rejected(
                new IdempotencyKey("idem-1"), new PostingRejection.DuplicateIdempotencyKey()));
    ByteArrayOutputStream firstOutput = new ByteArrayOutputStream();
    FinGrindCli firstCli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(firstOutput),
            fixedClock(),
            workflow);
    int firstExitCode =
        firstCli.run(
            new String[] {
              "post-entry",
              "--book-file",
              bookFilePath.toString(),
              "--request-file",
              requestFile.toString()
            });

    ByteArrayOutputStream secondOutput = new ByteArrayOutputStream();
    FinGrindCli secondCli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(secondOutput),
            fixedClock(),
            workflow);
    int secondExitCode =
        secondCli.run(
            new String[] {
              "post-entry",
              "--book-file",
              bookFilePath.toString(),
              "--request-file",
              requestFile.toString()
            });

    assertEquals(0, firstExitCode);
    assertEquals(2, secondExitCode);
    assertTrue(firstOutput.toString(StandardCharsets.UTF_8).contains("\"status\":\"committed\""));
    assertTrue(secondOutput.toString(StandardCharsets.UTF_8).contains("\"status\":\"rejected\""));
    assertTrue(
        secondOutput
            .toString(StandardCharsets.UTF_8)
            .contains("\"code\":\"duplicate-idempotency-key\""));
    assertEquals(List.of(bookFilePath, bookFilePath), workflow.commitPaths());
  }

  @Test
  void run_rejectsMissingBookFile() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {"preflight-entry"});

    assertEquals(2, exitCode);
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"code\":\"invalid-request\""));
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"argument\":\"--book-file\""));
  }

  @Test
  void run_mapsSqliteRuntimeFailureToRuntimeFailureWithSqliteHint() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            new FinGrindCli.EntryWorkflow() {
              @Override
              public PostEntryResult preflight(Path bookFilePath, PostEntryCommand command) {
                throw new IllegalStateException("Failed to start sqlite3.");
              }

              @Override
              public PostEntryResult commit(Path bookFilePath, PostEntryCommand command) {
                throw new IllegalStateException("Failed to start sqlite3.");
              }
            });

    int exitCode =
        cli.run(
            new String[] {
              "preflight-entry",
              "--book-file",
              tempDirectory.resolve("book.sqlite").toString(),
              "--request-file",
              requestFile.toString()
            });

    assertEquals(1, exitCode);
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"code\":\"runtime-failure\""));
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("FINGRIND_SQLITE3_BINARY"));
  }

  @Test
  void run_mapsGenericRuntimeFailureToRuntimeFailureWithGenericHint() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            new FinGrindCli.EntryWorkflow() {
              @Override
              public PostEntryResult preflight(Path bookFilePath, PostEntryCommand command) {
                throw new IllegalStateException("boom");
              }

              @Override
              public PostEntryResult commit(Path bookFilePath, PostEntryCommand command) {
                throw new IllegalStateException("boom");
              }
            });

    int exitCode =
        cli.run(
            new String[] {
              "preflight-entry",
              "--book-file",
              tempDirectory.resolve("book.sqlite").toString(),
              "--request-file",
              requestFile.toString()
            });

    assertEquals(1, exitCode);
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"code\":\"runtime-failure\""));
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains(
                "Inspect the message and rerun after fixing the underlying runtime problem."));
  }

  @Test
  void run_mapsGenericIllegalArgumentExceptionToInvalidRequest() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            new FinGrindCli.EntryWorkflow() {
              @Override
              public PostEntryResult preflight(Path bookFilePath, PostEntryCommand command) {
                throw new IllegalArgumentException("workflow boom");
              }

              @Override
              public PostEntryResult commit(Path bookFilePath, PostEntryCommand command) {
                throw new IllegalArgumentException("workflow boom");
              }
            });

    int exitCode =
        cli.run(
            new String[] {
              "preflight-entry",
              "--book-file",
              tempDirectory.resolve("book.sqlite").toString(),
              "--request-file",
              requestFile.toString()
            });

    assertEquals(2, exitCode);
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"code\":\"invalid-request\""));
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("workflow boom"));
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Run 'fingrind help'"));
  }

  @Test
  void run_mapsRequestValidationFailureToInvalidRequest() throws IOException {
    Path requestFile =
        writeRequest(
            """
            {
              "effectiveDate": "2026-04-07",
              "lines": []
            }
            """);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode =
        cli.run(
            new String[] {
              "preflight-entry",
              "--book-file",
              tempDirectory.resolve("book.sqlite").toString(),
              "--request-file",
              requestFile.toString()
            });

    assertEquals(2, exitCode);
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"code\":\"invalid-request\""));
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("Missing required field: provenance"));
  }

  @Test
  void run_doesNotTouchWorkflowForDiscoveryCommands() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            new PostEntryResult.Committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z")));
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);

    int exitCode = cli.run(new String[] {"capabilities"});

    assertEquals(0, exitCode);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"status\""));
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("\"ok\""));
    assertFalse(workflow.preflightInvoked());
    assertFalse(workflow.commitInvoked());
  }

  private Path writeRequest(String payload) throws IOException {
    Path requestFile = tempDirectory.resolve("request.json");
    Files.writeString(requestFile, payload, StandardCharsets.UTF_8);
    return requestFile;
  }

  private static Clock fixedClock() {
    return Clock.fixed(Instant.parse("2026-04-07T12:00:00Z"), ZoneOffset.UTC);
  }

  private static String validRequestJson() {
    return """
            {
              "effectiveDate": "2026-04-07",
              "lines": [
                {
                  "accountCode": "1000",
                  "side": "DEBIT",
                  "currencyCode": "EUR",
                  "amount": "10.00"
                },
                {
                  "accountCode": "2000",
                  "side": "CREDIT",
                  "currencyCode": "EUR",
                  "amount": "10.00"
                }
              ],
              "provenance": {
                "actorId": "actor-1",
                "actorType": "AGENT",
                "commandId": "command-1",
                "idempotencyKey": "idem-1",
                "causationId": "cause-1"
              }
            }
            """;
  }

  private static PrintStream utf8PrintStream(ByteArrayOutputStream outputStream) {
    return new PrintStream(outputStream, false, StandardCharsets.UTF_8);
  }

  /** Recording workflow used to assert CLI routing without opening SQLite. */
  private static final class RecordingWorkflow implements FinGrindCli.EntryWorkflow {
    private final List<Path> preflightPaths = new ArrayList<>();
    private final List<Path> commitPaths = new ArrayList<>();
    private final PostEntryResult preflightResult;
    private final List<PostEntryResult> commitResults;
    private int commitIndex;

    private RecordingWorkflow(PostEntryResult preflightResult, PostEntryResult... commitResults) {
      this.preflightResult = preflightResult;
      this.commitResults = List.of(commitResults);
    }

    @Override
    public PostEntryResult preflight(Path bookFilePath, PostEntryCommand command) {
      preflightPaths.add(bookFilePath);
      return preflightResult;
    }

    @Override
    public PostEntryResult commit(Path bookFilePath, PostEntryCommand command) {
      commitPaths.add(bookFilePath);
      PostEntryResult result = commitResults.get(commitIndex);
      commitIndex += 1;
      return result;
    }

    private List<Path> preflightPaths() {
      return preflightPaths;
    }

    private List<Path> commitPaths() {
      return commitPaths;
    }

    private boolean preflightInvoked() {
      return !preflightPaths.isEmpty();
    }

    private boolean commitInvoked() {
      return !commitPaths.isEmpty();
    }
  }
}
