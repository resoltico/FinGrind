package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PostEntryResult;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Unit tests for {@link FinGrindCli}. */
@NullUnmarked
class FinGrindCliInputFailureTest extends FinGrindCliTestSupport {
  @Test
  void run_mapsCliRequestExceptionToInvalidRequestWithoutInvokingWorkflow() throws IOException {
    Path requestFile = writeNamedRequest("broken-declare-account.json", "{");
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            new OpenBookResult.Opened(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                new DeclaredAccount(
                    new AccountCode("1000"),
                    new AccountName("Cash"),
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            new ListAccountsResult.Listed(new AccountPage(List.of(), 50, Optional.empty())),
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            new PostEntryResult.Committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z")));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);

    int exitCode =
        cli.run(
            new String[] {
              "declare-account",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            });

    assertEquals(1, exitCode);
    JsonNode failureEnvelope =
        CliJsonRequestCodec.configuredObjectMapper().readTree(outputStream.toByteArray());
    assertEquals("error", failureEnvelope.path("status").stringValue());
    assertEquals("invalid-request", failureEnvelope.path("code").stringValue());
    assertEquals(
        "Failed to read request JSON at line 1, column 2.",
        failureEnvelope.path("message").stringValue());
    assertTrue(
        failureEnvelope
            .path("details")
            .path("parseMessage")
            .stringValue()
            .startsWith("Unexpected end-of-input: expected close marker for Object"));
    assertEquals(1, failureEnvelope.path("details").path("line").intValue());
    assertEquals(2, failureEnvelope.path("details").path("column").intValue());
    assertFalse(workflow.workflowInvoked());
  }

  @Test
  void run_reportsMissingRequestFileAsARequestTransportFailure() throws IOException {
    Path requestFile = tempDirectory.resolve("missing-declare-account.json");
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            new OpenBookResult.Opened(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                new DeclaredAccount(
                    new AccountCode("1000"),
                    new AccountName("Cash"),
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            new ListAccountsResult.Listed(new AccountPage(List.of(), 50, Optional.empty())),
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            new PostEntryResult.Committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z")));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);

    int exitCode =
        cli.run(
            new String[] {
              "declare-account",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            });

    assertEquals(1, exitCode);
    JsonNode failureEnvelope =
        CliJsonRequestCodec.configuredObjectMapper().readTree(outputStream.toByteArray());
    assertEquals("error", failureEnvelope.path("status").stringValue());
    assertEquals("invalid-request", failureEnvelope.path("code").stringValue());
    assertEquals(
        "Request file does not exist: " + requestFile.toAbsolutePath().normalize() + ".",
        failureEnvelope.path("message").stringValue());
    assertTrue(
        failureEnvelope
            .path("hint")
            .stringValue()
            .contains("Verify that the selected --request-file exists and is readable"));
    assertFalse(workflow.workflowInvoked());
  }

  @Test
  void run_rendersCliRequestExceptionInHumanMode() throws IOException {
    Path requestFile = writeNamedRequest("broken-declare-account-human.json", "{");
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            new OpenBookResult.Opened(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                new DeclaredAccount(
                    new AccountCode("1000"),
                    new AccountName("Cash"),
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            new ListAccountsResult.Listed(new AccountPage(List.of(), 50, Optional.empty())),
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            new PostEntryResult.Committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z")));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);

    int exitCode =
        cli.run(
            new String[] {
              "declare-account",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString(),
              "--output",
              "human"
            });

    assertEquals(1, exitCode);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Error"));
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("invalid-request"));
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("Failed to read request JSON at line 1, column 2."));
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Parse message"));
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Parse location"));
    assertFalse(outputStream.toString(StandardCharsets.UTF_8).contains("\"status\""));
    assertFalse(workflow.workflowInvoked());
  }

  @Test
  void run_emitsStructuredJournalViolationsForInvalidRequest() throws IOException {
    Path requestFile =
        writeNamedRequest(
            "invalid-journal-request.json",
            """
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
                  "side": "DEBIT",
                  "currencyCode": "USD",
                  "amount": "5.00"
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
            """);
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            new OpenBookResult.Opened(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                new DeclaredAccount(
                    new AccountCode("1000"),
                    new AccountName("Cash"),
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            new ListAccountsResult.Listed(new AccountPage(List.of(), 50, Optional.empty())),
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            new PostEntryResult.Committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z")));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
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
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            });

    assertEquals(1, exitCode);
    JsonNode failureEnvelope =
        CliJsonRequestCodec.configuredObjectMapper().readTree(outputStream.toByteArray());
    assertEquals("error", failureEnvelope.path("status").stringValue());
    assertEquals("invalid-request", failureEnvelope.path("code").stringValue());
    assertEquals(
        List.of(
            "Journal entry lines must share one currency.",
            "Journal entry must contain at least one debit line and one credit line.",
            "Journal entry must balance debits and credits."),
        List.of(
            failureEnvelope.path("details").path("violations").get(0).stringValue(),
            failureEnvelope.path("details").path("violations").get(1).stringValue(),
            failureEnvelope.path("details").path("violations").get(2).stringValue()));
    assertFalse(workflow.workflowInvoked());
  }

  @Test
  void run_guidesFlattenedLedgerPlanDeclareAccountStepsBackToTheCanonicalNestedShape()
      throws IOException {
    Path requestFile =
        writeNamedRequest(
            "invalid-ledger-plan-flattened-declare-account.json",
            """
            {
              "planId": "plan-1",
              "steps": [
                {
                  "stepId": "step-1",
                  "kind": "declare-account",
                  "accountCode": "1000",
                  "accountName": "Cash",
                  "normalBalance": "DEBIT"
                }
              ]
            }
            """);
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            new OpenBookResult.Opened(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                new DeclaredAccount(
                    new AccountCode("1000"),
                    new AccountName("Cash"),
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            new ListAccountsResult.Listed(new AccountPage(List.of(), 50, Optional.empty())),
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            new PostEntryResult.Committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z")));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);

    int exitCode =
        cli.run(
            new String[] {
              "execute-plan",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            });

    assertEquals(1, exitCode);
    JsonNode failureEnvelope =
        CliJsonRequestCodec.configuredObjectMapper().readTree(outputStream.toByteArray());
    assertEquals("error", failureEnvelope.path("status").stringValue());
    assertEquals("invalid-request", failureEnvelope.path("code").stringValue());
    assertEquals(
        "Fields accountCode, accountName, normalBalance must be nested under declareAccount for declare-account ledger plan steps.",
        failureEnvelope.path("message").stringValue());
    assertTrue(
        failureEnvelope
            .path("hint")
            .stringValue()
            .contains(CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE)));
    assertFalse(workflow.workflowInvoked());
  }

  @Test
  void run_mapsReversedEffectiveDateRangeArgumentsToInvalidRequest() throws IOException {
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            new OpenBookResult.Opened(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                new DeclaredAccount(
                    new AccountCode("1000"),
                    new AccountName("Cash"),
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            new ListAccountsResult.Listed(new AccountPage(List.of(), 50, Optional.empty())),
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            new PostEntryResult.Committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z")));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);

    int exitCode =
        cli.run(
            new String[] {
              "account-ledger",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--account-code",
              "1000",
              "--effective-date-from",
              "2026-04-30",
              "--effective-date-to",
              "2026-04-01"
            });

    assertEquals(1, exitCode);
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("\"code\":\"invalid-request\""));
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("effectiveDateFrom must be on or before effectiveDateTo."));
    assertFalse(workflow.workflowInvoked());
  }

  @Test
  void run_rendersCliArgumentsExceptionInHumanMode() throws IOException {
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            new OpenBookResult.Opened(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                new DeclaredAccount(
                    new AccountCode("1000"),
                    new AccountName("Cash"),
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            new ListAccountsResult.Listed(new AccountPage(List.of(), 50, Optional.empty())),
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            new PostEntryResult.Committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z")));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);

    int exitCode =
        cli.run(
            new String[] {
              "open-book",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--output",
              "human",
              "--bogus"
            });

    assertEquals(1, exitCode);
    assertTrue(outputStream.toString(StandardCharsets.UTF_8).contains("Error"));
    assertTrue(
        outputStream.toString(StandardCharsets.UTF_8).contains("Unsupported argument: --bogus"));
    assertFalse(outputStream.toString(StandardCharsets.UTF_8).contains("\"status\""));
    assertFalse(workflow.workflowInvoked());
  }
}
