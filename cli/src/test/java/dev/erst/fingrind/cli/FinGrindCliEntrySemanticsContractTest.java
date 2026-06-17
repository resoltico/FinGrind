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
    JsonNode rejectionEnvelope =
        CliJsonObjectMappers.configuredObjectMapper().readTree(diagnostics);
    assertEquals("rejected", rejectionEnvelope.path("status").stringValue(), diagnostics);
    assertEquals(
        "entry-semantics-violations", rejectionEnvelope.path("code").stringValue(), diagnostics);
    assertTrue(hasDistinctRoleViolation(rejectionEnvelope), diagnostics);
    assertTrue(diagnostics.contains(scenario.firstField()), diagnostics);
    assertTrue(diagnostics.contains(scenario.secondField()), diagnostics);
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
