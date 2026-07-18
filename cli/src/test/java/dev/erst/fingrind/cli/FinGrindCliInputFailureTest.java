package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
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
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Unit tests for {@link FinGrindCli}. */
class FinGrindCliInputFailureTest extends FinGrindCliTestSupport {
  @Test
  void run_mapsCliRequestExceptionToInvalidRequestWithoutInvokingWorkflow() throws IOException {
    Path requestFile = writeNamedRequest("broken-declare-account.json", "{");
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            openedBookResult(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                declaredAccount(
                    "1000",
                    "Cash",
                    dev.erst.fingrind.core.AccountType.ASSET,
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            listedAccounts(accountPage(List.of(), 50, Optional.empty())),
            CliPostEntryResultFixtures.preflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            CliPostEntryResultFixtures.committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z"),
                false));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);
    int exitCode =
        cli.run(
            jsonArguments(
                "declare-account",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                requestFile.toString()));
    assertEquals(1, exitCode);
    JsonNode failureEnvelope =
        CliJsonObjectMappers.configuredObjectMapper().readTree(outputStream.toByteArray());
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
            openedBookResult(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                declaredAccount(
                    "1000",
                    "Cash",
                    dev.erst.fingrind.core.AccountType.ASSET,
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            listedAccounts(accountPage(List.of(), 50, Optional.empty())),
            CliPostEntryResultFixtures.preflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            CliPostEntryResultFixtures.committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z"),
                false));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);
    int exitCode =
        cli.run(
            jsonArguments(
                "declare-account",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                requestFile.toString()));
    assertEquals(1, exitCode);
    JsonNode failureEnvelope =
        CliJsonObjectMappers.configuredObjectMapper().readTree(outputStream.toByteArray());
    assertEquals("error", failureEnvelope.path("status").stringValue());
    assertEquals("invalid-request", failureEnvelope.path("code").stringValue());
    assertEquals("Request file does not exist.", failureEnvelope.path("message").stringValue());
    assertEquals(
        CliPublicPaths.absoluteValue(requestFile), failureEnvelope.path("path").stringValue());
    assertTrue(
        failureEnvelope
            .path("hint")
            .stringValue()
            .contains("Verify that the selected --request-file exists and is readable"));
    assertFalse(workflow.workflowInvoked());
  }

  @Test
  void run_reportsOversizedRequestFilesAsInvalidRequestWithoutInvokingWorkflow()
      throws IOException {
    Path requestFile =
        writeNamedRequest(
            "oversized-declare-account.json",
            "{\"padding\":\""
                + "a".repeat(ProtocolInteractionLimits.REQUEST_PAYLOAD_MAX_BYTES)
                + "\"}");
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            openedBookResult(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                declaredAccount(
                    "1000",
                    "Cash",
                    dev.erst.fingrind.core.AccountType.ASSET,
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            listedAccounts(accountPage(List.of(), 50, Optional.empty())),
            CliPostEntryResultFixtures.preflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            CliPostEntryResultFixtures.committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z"),
                false));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);

    int exitCode =
        cli.run(
            jsonArguments(
                "declare-account",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                requestFile.toString()));

    assertEquals(1, exitCode);
    JsonNode failureEnvelope =
        CliJsonObjectMappers.configuredObjectMapper().readTree(outputStream.toByteArray());
    assertEquals("error", failureEnvelope.path("status").stringValue());
    assertEquals("invalid-request", failureEnvelope.path("code").stringValue());
    assertEquals(
        "Request file exceeded the supported "
            + ProtocolInteractionLimits.REQUEST_PAYLOAD_MAX_BYTES
            + "-byte UTF-8 limit.",
        failureEnvelope.path("message").stringValue());
    assertEquals(
        CliPublicPaths.absoluteValue(requestFile), failureEnvelope.path("path").stringValue());
    assertTrue(failureEnvelope.path("hint").stringValue().contains("split the work into smaller"));
    assertFalse(workflow.workflowInvoked());
  }

  @Test
  void run_emitsJsonCliRequestFailureWhenTextModeIsSelected() throws IOException {
    Path requestFile = writeNamedRequest("broken-declare-account-text.json", "{");
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            openedBookResult(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                declaredAccount(
                    "1000",
                    "Cash",
                    dev.erst.fingrind.core.AccountType.ASSET,
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            listedAccounts(accountPage(List.of(), 50, Optional.empty())),
            CliPostEntryResultFixtures.preflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            CliPostEntryResultFixtures.committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z"),
                false));
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
              "text"
            });
    assertEquals(1, exitCode);
    String failureText = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(failureText.contains("Error"));
    assertTrue(failureText.contains("invalid-request"));
    assertTrue(failureText.contains("Failed to read request JSON at line 1, column 2."));
    assertTrue(failureText.contains("Unexpected end-of-input: expected close marker for Object"));
    assertTrue(failureText.contains("line 1, column 2"));
    assertFalse(workflow.workflowInvoked());
  }

  @Test
  void run_emitsStructuredJournalViolationsForInvalidRequest() throws IOException {
    Path requestFile =
        writeNamedRequest(
            "invalid-journal-request.json",
            CliRequestReaderTestSupport.withEvidence(
                """
            {
              "entryKind": "DIRECT_JOURNAL",
              "effectiveDate": "2026-04-07",
              "lines": %s,
              "provenance": {
                "actorId": "actor-1",
                "actorType": "AGENT",
                "commandId": "command-1",
                "idempotencyKey": "idem-1",
                "causationId": "cause-1"
              }
            }
            """
                    .formatted(
                        CliRequestReaderTestSupport.journalLinesJson(
                            "1000",
                            "DEBIT",
                            CliRequestReaderTestSupport.eurMoneyJson("1000"),
                            "2000",
                            "DEBIT",
                            CliRequestReaderTestSupport.moneyJson("USD", "500")))));
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            openedBookResult(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                declaredAccount(
                    "1000",
                    "Cash",
                    dev.erst.fingrind.core.AccountType.ASSET,
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            listedAccounts(accountPage(List.of(), 50, Optional.empty())),
            CliPostEntryResultFixtures.preflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            CliPostEntryResultFixtures.committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z"),
                false));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);
    int exitCode =
        cli.run(
            jsonArguments(
                "preflight-entry",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                requestFile.toString()));
    assertEquals(1, exitCode);
    JsonNode failureEnvelope =
        CliJsonObjectMappers.configuredObjectMapper().readTree(outputStream.toByteArray());
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
                  "accountType": "ASSET"
                }
              ]
            }
            """);
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            openedBookResult(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                declaredAccount(
                    "1000",
                    "Cash",
                    dev.erst.fingrind.core.AccountType.ASSET,
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            listedAccounts(accountPage(List.of(), 50, Optional.empty())),
            CliPostEntryResultFixtures.preflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            CliPostEntryResultFixtures.committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z"),
                false));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);
    int exitCode =
        cli.run(
            jsonArguments(
                "execute-plan",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                requestFile.toString()));
    assertEquals(1, exitCode);
    JsonNode failureEnvelope =
        CliJsonObjectMappers.configuredObjectMapper().readTree(outputStream.toByteArray());
    assertEquals("error", failureEnvelope.path("status").stringValue());
    assertEquals("invalid-request", failureEnvelope.path("code").stringValue());
    assertEquals(
        "Fields accountCode, accountName, accountType must be nested under declareAccount for declare-account ledger plan steps.",
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
            openedBookResult(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                declaredAccount(
                    "1000",
                    "Cash",
                    dev.erst.fingrind.core.AccountType.ASSET,
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            listedAccounts(accountPage(List.of(), 50, Optional.empty())),
            CliPostEntryResultFixtures.preflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            CliPostEntryResultFixtures.committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z"),
                false));
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
              "2026-04-01",
              "--output",
              "json"
            });
    assertEquals(1, exitCode);
    assertJsonContains(outputStream, "\"code\":\"invalid-request\"");
    assertTrue(
        outputStream
            .toString(StandardCharsets.UTF_8)
            .contains("--effective-date-from must be on or before --effective-date-to."));
    assertFalse(workflow.workflowInvoked());
  }

  @Test
  void run_emitsJsonCliArgumentsFailureWhenTextModeIsSelected() throws IOException {
    Path bookFilePath = tempDirectory.resolve("book.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            openedBookResult(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                declaredAccount(
                    "1000",
                    "Cash",
                    dev.erst.fingrind.core.AccountType.ASSET,
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            listedAccounts(accountPage(List.of(), 50, Optional.empty())),
            CliPostEntryResultFixtures.preflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            CliPostEntryResultFixtures.committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z"),
                false));
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
              "--entity-name",
              "Acme Studio",
              "--book-template-id",
              "OWNER_MANAGED_SERVICE",
              "--accounting-basis",
              "CASH",
              "--functional-currency",
              "EUR",
              "--fiscal-year-start",
              "01-01",
              "--book-start-effective-date",
              "2026-01-01",
              "--book-start-effective-date",
              "2026-01-01",
              "--output",
              "text",
              "--bogus"
            });
    assertEquals(1, exitCode);
    String failureText = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(failureText.contains("Error"));
    assertTrue(failureText.contains("invalid-request"));
    assertTrue(failureText.contains("Unsupported argument: --bogus"));
    assertFalse(workflow.workflowInvoked());
  }

  @Test
  void run_rendersDeterministicJsonForBlankUnsupportedArgument() throws IOException {
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            openedBookResult(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                declaredAccount(
                    "1000",
                    "Cash",
                    dev.erst.fingrind.core.AccountType.ASSET,
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            listedAccounts(accountPage(List.of(), 50, Optional.empty())),
            CliPostEntryResultFixtures.preflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            CliPostEntryResultFixtures.committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z"),
                false));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);

    int exitCode = cli.run(new String[] {"capabilities", "--output", "json", " "});

    assertEquals(1, exitCode);
    String output = outputStream.toString(StandardCharsets.UTF_8);
    assertJsonContains(outputStream, "\"code\":\"invalid-request\"");
    assertTrue(output.contains("Unsupported argument:"));
    assertFalse(output.contains("\"argument\""));
    assertFalse(output.contains("IllegalArgumentException"));
    assertFalse(workflow.workflowInvoked());
  }
}
