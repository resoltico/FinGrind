package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** End-to-end coverage for typed-entry semantic rejections at the public CLI boundary. */
class FinGrindCliEntrySemanticsContractTest extends FinGrindCliTestSupport {
  @Test
  void run_rejectsEconomicNullJournalWithLineOwnedNarrativeAcrossPreflightAndCommit()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("economic-null-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path declareCashFile =
        writeNamedRequest("declare-cash.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path requestFile =
        writeNamedRequest("economic-null-journal.json", economicNullJournalRequestJson());

    openBook(bookFilePath, bookKeyFilePath);
    declareAccount(bookFilePath, bookKeyFilePath, declareCashFile);

    String expectedMessage = null;
    for (String commandName : List.of("preflight-entry", "post-entry")) {
      JsonNode rejectionEnvelope =
          runRejectedEnvelope(bookFilePath, bookKeyFilePath, requestFile, commandName);
      String message = rejectionEnvelope.path("message").stringValue();

      assertTrue(
          hasViolation(rejectionEnvelope, "economic-null-journal"),
          rejectionEnvelope.toPrettyString());
      assertEquals("Posting rejected with 1 entry-semantics issue.", message);
      assertFalse(message.contains("published semantics"), message);
      assertTrue(
          rejectionEnvelope.path("hint").isMissingNode(), rejectionEnvelope.toPrettyString());

      if (expectedMessage == null) {
        expectedMessage = message;
      } else {
        assertEquals(expectedMessage, message);
      }
    }
  }

  @Test
  void run_rejectsSameAccountTypedEntriesAcrossPreflightCommitAndOutputModes() throws IOException {
    Path bookFilePath = tempDirectory.resolve("same-account-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path declareCashFile =
        writeNamedRequest("declare-cash.json", declareAccountJson("1000", "Cash", "DEBIT"));

    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(jsonArguments(openBookKeyFileArguments(bookFilePath, bookKeyFilePath))));
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                jsonArguments(
                    "declare-account",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--request-file",
                    declareCashFile.toString())));

    for (SameAccountCase scenario : sameAccountCases()) {
      Path requestFile =
          writeNamedRequest(
              "same-account-%s.json".formatted(scenario.slug()), scenario.requestJson());
      for (String commandName : List.of("preflight-entry", "post-entry")) {
        for (String outputMode : List.of("json", "text")) {
          assertSameAccountRejection(
              bookFilePath, bookKeyFilePath, requestFile, scenario, commandName, outputMode);
        }
      }
    }
  }

  @Test
  void run_composesSpecificEntrySemanticsEnvelopeAcrossPreflightAndCommit() throws IOException {
    Path bookFilePath = tempDirectory.resolve("multi-violation-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path declareCashFile =
        writeNamedRequest("declare-cash.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path requestFile =
        writeNamedRequest("multi-violation-request.json", multiViolationTypedEntryRequestJson());

    openBook(bookFilePath, bookKeyFilePath);
    declareAccount(bookFilePath, bookKeyFilePath, declareCashFile);

    String expectedMessage = null;
    for (String commandName : List.of("preflight-entry", "post-entry")) {
      JsonNode rejectionEnvelope =
          runRejectedEnvelope(bookFilePath, bookKeyFilePath, requestFile, commandName);
      String message = rejectionEnvelope.path("message").stringValue();

      assertTrue(hasViolation(rejectionEnvelope, "distinct-role-accounts-required"));
      assertTrue(hasViolation(rejectionEnvelope, "account-type-mismatch"));
      assertTrue(hasViolation(rejectionEnvelope, "source-document-type-not-accepted"));
      assertFalse(message.contains("published semantics"), message);
      assertEquals("Posting rejected with 3 entry-semantics issues.", message);
      assertTrue(
          rejectionEnvelope.path("hint").isMissingNode(), rejectionEnvelope.toPrettyString());

      if (expectedMessage == null) {
        expectedMessage = message;
      } else {
        assertEquals(expectedMessage, message);
      }
    }
  }

  @Test
  void run_rejectsDistinctRoleCollisionsWithAccountOwnedNarrativeAcrossPreflightAndCommit()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("distinct-role-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path declareCashFile =
        writeNamedRequest("declare-cash.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path requestFile =
        writeNamedRequest("distinct-role-collision.json", distinctRoleCollisionRequestJson());

    openBook(bookFilePath, bookKeyFilePath);
    declareAccount(bookFilePath, bookKeyFilePath, declareCashFile);

    String expectedMessage = null;
    for (String commandName : List.of("preflight-entry", "post-entry")) {
      JsonNode rejectionEnvelope =
          runRejectedEnvelope(bookFilePath, bookKeyFilePath, requestFile, commandName);
      String message = rejectionEnvelope.path("message").stringValue();

      assertTrue(
          hasViolation(rejectionEnvelope, "distinct-role-accounts-required"),
          rejectionEnvelope.toPrettyString());
      assertTrue(message.startsWith("Posting rejected with "), message);
      assertTrue(message.contains("entry-semantics issue"), message);
      assertFalse(message.contains("published semantics"), message);
      assertTrue(
          rejectionEnvelope.path("hint").isMissingNode(), rejectionEnvelope.toPrettyString());

      if (expectedMessage == null) {
        expectedMessage = message;
      } else {
        assertEquals(expectedMessage, message);
      }
    }
  }

  private void assertSameAccountRejection(
      Path bookFilePath,
      Path bookKeyFilePath,
      Path requestFile,
      SameAccountCase scenario,
      String commandName,
      String outputMode)
      throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsStream),
            fixedClock());

    int exitCode =
        cli.run(
            new String[] {
              commandName,
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString(),
              "--output",
              outputMode
            });

    assertEquals(2, exitCode, scenario.slug() + ":" + commandName + ":" + outputMode);
    assertEquals("", outputStream.toString(StandardCharsets.UTF_8));
    String diagnostics = diagnosticsStream.toString(StandardCharsets.UTF_8);
    if ("json".equals(outputMode)) {
      JsonNode rejectionEnvelope =
          CliJsonObjectMappers.configuredObjectMapper().readTree(diagnostics);
      assertEquals("rejected", rejectionEnvelope.path("status").stringValue(), diagnostics);
      assertEquals(
          "entry-semantics-violations", rejectionEnvelope.path("code").stringValue(), diagnostics);
      assertTrue(rejectionEnvelope.path("hint").isMissingNode(), diagnostics);
      assertTrue(hasDistinctRoleViolation(rejectionEnvelope), diagnostics);
    } else {
      assertTrue(diagnostics.contains("Rejected"), diagnostics);
      assertTrue(diagnostics.contains("entry-semantics-violations"), diagnostics);
      assertTrue(diagnostics.contains("Summary"), diagnostics);
      assertTrue(diagnostics.contains("Issue 1 | distinct-role-accounts-required"), diagnostics);
      assertTrue(diagnostics.contains(scenario.firstField()), diagnostics);
      assertTrue(diagnostics.contains(scenario.secondField()), diagnostics);
      assertTrue(diagnostics.contains("Why"), diagnostics);
      assertFalse(diagnostics.contains("Hint"), diagnostics);
    }
    assertFalse(diagnostics.contains("Exception"), diagnostics);
    assertFalse(diagnostics.contains("\tat "), diagnostics);
  }

  private static boolean hasDistinctRoleViolation(JsonNode rejectionEnvelope) {
    return StreamSupport.stream(
            rejectionEnvelope.path("details").path("violations").spliterator(), false)
        .anyMatch(
            violation ->
                "distinct-role-accounts-required".equals(violation.path("code").stringValue()));
  }

  private JsonNode runRejectedEnvelope(
      Path bookFilePath, Path bookKeyFilePath, Path requestFile, String commandName)
      throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsStream),
            fixedClock());

    int exitCode =
        cli.run(
            jsonArguments(
                commandName,
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                requestFile.toString()));

    assertEquals(2, exitCode, commandName);
    assertEquals("", outputStream.toString(StandardCharsets.UTF_8));
    JsonNode rejectionEnvelope =
        CliJsonObjectMappers.configuredObjectMapper()
            .readTree(diagnosticsStream.toString(StandardCharsets.UTF_8));
    assertEquals("rejected", rejectionEnvelope.path("status").stringValue());
    assertEquals("entry-semantics-violations", rejectionEnvelope.path("code").stringValue());
    return rejectionEnvelope;
  }

  private void openBook(Path bookFilePath, Path bookKeyFilePath) {
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(jsonArguments(openBookKeyFileArguments(bookFilePath, bookKeyFilePath))));
  }

  private void declareAccount(Path bookFilePath, Path bookKeyFilePath, Path requestFile) {
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                jsonArguments(
                    "declare-account",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--request-file",
                    requestFile.toString())));
  }

  private static boolean hasViolation(JsonNode rejectionEnvelope, String code) {
    return StreamSupport.stream(
            rejectionEnvelope.path("details").path("violations").spliterator(), false)
        .anyMatch(violation -> code.equals(violation.path("code").stringValue()));
  }

  private static String economicNullJournalRequestJson() {
    return """
        {
          "entryKind": "JOURNAL",
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            },
            {
              "accountCode": "1000",
              "side": "CREDIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            }
          ],
          "evidence": {
            "sourceDocuments": [
              {
                "sourceDocumentId": "document-economic-null",
                "sourceDocumentType": "working-note",
                "documentDate": "2026-04-07",
                "capturedAt": "2026-04-07T10:15:30Z",
                "storageLocator": "vault://fixtures/document-economic-null",
                "contentSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
              }
            ],
            "approvals": []
          },
          "provenance": {
            "actorId": "actor-economic-null",
            "actorType": "AGENT",
            "commandId": "command-economic-null",
            "idempotencyKey": "idem-economic-null",
            "causationId": "cause-economic-null"
          }
        }
        """;
  }

  private static String multiViolationTypedEntryRequestJson() {
    return """
        {
          "entryKind": "JOURNAL",
          "recipeKind": "CASH_REVENUE",
          "effectiveDate": "2026-04-07",
          "cashAccountCode": "1000",
          "revenueAccountCode": "1000",
          "amount": {
            "currencyCode": "EUR",
            "minorUnits": "1000"
          },
          "evidence": {
            "sourceDocuments": [
              {
                "sourceDocumentId": "document-multi-violation",
                "sourceDocumentType": "invoice",
                "documentDate": "2026-04-07",
                "capturedAt": "2026-04-07T10:15:30Z",
                "storageLocator": "vault://fixtures/document-multi-violation",
                "contentSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
              }
            ],
            "approvals": []
          },
          "provenance": {
            "actorId": "actor-multi-violation",
            "actorType": "AGENT",
            "commandId": "command-multi-violation",
            "idempotencyKey": "idem-multi-violation",
            "causationId": "cause-multi-violation"
          }
        }
        """;
  }

  private static String distinctRoleCollisionRequestJson() {
    return """
        {
          "entryKind": "JOURNAL",
          "recipeKind": "CASH_REVENUE",
          "effectiveDate": "2026-04-07",
          "cashAccountCode": "1000",
          "revenueAccountCode": "1000",
          "amount": {
            "currencyCode": "EUR",
            "minorUnits": "1000"
          },
          "evidence": {
            "sourceDocuments": [
              {
                "sourceDocumentId": "document-distinct-role",
                "sourceDocumentType": "cash-receipt",
                "documentDate": "2026-04-07",
                "capturedAt": "2026-04-07T10:15:30Z",
                "storageLocator": "vault://fixtures/document-distinct-role",
                "contentSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
              }
            ],
            "approvals": []
          },
          "provenance": {
            "actorId": "actor-distinct-role",
            "actorType": "AGENT",
            "commandId": "command-distinct-role",
            "idempotencyKey": "idem-distinct-role",
            "causationId": "cause-distinct-role"
          }
        }
        """;
  }

  private static List<SameAccountCase> sameAccountCases() {
    return List.of(
        new SameAccountCase(
            "cash-revenue",
            "CASH_REVENUE",
            "cashAccountCode",
            "revenueAccountCode",
            "cash-receipt"),
        new SameAccountCase(
            "cash-expense",
            "CASH_EXPENSE",
            "expenseAccountCode",
            "cashAccountCode",
            "expense-receipt"),
        new SameAccountCase(
            "equity-contribution",
            "EQUITY_CONTRIBUTION",
            "cashAccountCode",
            "equityAccountCode",
            "equity-contribution"),
        new SameAccountCase(
            "equity-withdrawal",
            "EQUITY_WITHDRAWAL",
            "equityAccountCode",
            "cashAccountCode",
            "equity-withdrawal"));
  }

  private record SameAccountCase(
      String slug,
      String recipeKind,
      String firstField,
      String secondField,
      String sourceDocumentType) {
    private String requestJson() {
      return """
          {
            "entryKind": "JOURNAL",
            "recipeKind": "%s",
            "effectiveDate": "2026-04-07",
            "%s": "1000",
            "%s": "1000",
            "amount": {
              "currencyCode": "EUR",
              "minorUnits": "1000"
            },
            "evidence": {
              "sourceDocuments": [
                {
                  "sourceDocumentId": "document-%s",
                  "sourceDocumentType": "%s",
                  "documentDate": "2026-04-07",
                  "capturedAt": "2026-04-07T10:15:30Z",
                  "storageLocator": "vault://fixtures/document-%s",
                  "contentSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                }
              ],
              "approvals": []
            },
            "provenance": {
              "actorId": "actor-%s",
              "actorType": "AGENT",
              "commandId": "command-%s",
              "idempotencyKey": "idem-%s",
              "causationId": "cause-%s"
            }
          }
          """
          .formatted(
              recipeKind,
              firstField,
              secondField,
              slug,
              sourceDocumentType,
              slug,
              slug,
              slug,
              slug,
              slug);
    }
  }
}
