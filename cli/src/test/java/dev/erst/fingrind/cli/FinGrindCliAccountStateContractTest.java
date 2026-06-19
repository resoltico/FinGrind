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

/** End-to-end coverage for account-state rejection envelopes at the public CLI boundary. */
class FinGrindCliAccountStateContractTest extends FinGrindCliTestSupport {
  @Test
  void run_rejectsUnknownAccountWithUniformViolationCoreAcrossPreflightCommitAndOutputModes()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("account-state-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path declareCashFile =
        writeNamedRequest("declare-cash.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path requestFile = writeNamedRequest("unknown-account.json", unknownAccountRequestJson());

    openBook(bookFilePath, bookKeyFilePath);
    declareAccount(bookFilePath, bookKeyFilePath, declareCashFile);

    String expectedMessage = null;
    for (String commandName : List.of("preflight-entry", "post-entry")) {
      for (String outputMode : List.of("json", "text")) {
        ObservedInvocation observed =
            runEntryCommand(commandName, outputMode, bookFilePath, bookKeyFilePath, requestFile);

        assertEquals(2, observed.exitCode(), commandName + ":" + outputMode);
        assertEquals("", observed.stdout());
        String diagnostics = observed.stderr();
        if ("json".equals(outputMode)) {
          JsonNode rejectionEnvelope =
              CliJsonObjectMappers.configuredObjectMapper().readTree(diagnostics);
          String message = rejectionEnvelope.path("message").stringValue();

          assertEquals("rejected", rejectionEnvelope.path("status").stringValue(), diagnostics);
          assertEquals("account-state-violations", rejectionEnvelope.path("code").stringValue());
          assertEquals("Posting rejected with 1 account-state issue.", message);
          assertTrue(
              rejectionEnvelope.path("hint").isMissingNode(), rejectionEnvelope.toPrettyString());
          assertTrue(
              hasAccountStateViolation(rejectionEnvelope, "unknown-account"),
              rejectionEnvelope.toPrettyString());
          JsonNode violation = rejectionEnvelope.path("details").path("violations").get(0);
          assertEquals("lines[].accountCode", violation.path("field").stringValue());
          assertEquals("account-registry", violation.path("category").stringValue());
          assertEquals("9998", violation.path("accountCode").stringValue());
          assertTrue(violation.path("message").stringValue().contains("undeclared account '9998'"));
          assertTrue(
              violation.path("repair").stringValue().contains("Declare the missing account"));

          if (expectedMessage == null) {
            expectedMessage = message;
          } else {
            assertEquals(expectedMessage, message);
          }
        } else {
          assertTrue(diagnostics.contains("Rejected"), diagnostics);
          assertTrue(diagnostics.contains("account-state-violations"), diagnostics);
          assertTrue(diagnostics.contains("Summary"), diagnostics);
          assertTrue(diagnostics.contains("Issue 1 | unknown-account"), diagnostics);
          assertTrue(diagnostics.contains("lines[].accountCode"), diagnostics);
          assertTrue(diagnostics.contains("account-registry"), diagnostics);
          assertTrue(diagnostics.contains("9998"), diagnostics);
          assertTrue(diagnostics.contains("Why"), diagnostics);
          assertTrue(diagnostics.contains("Declare the missing account"), diagnostics);
          assertFalse(diagnostics.contains("Hint"), diagnostics);
        }
        assertFalse(diagnostics.contains("Exception"), diagnostics);
        assertFalse(diagnostics.contains("\tat "), diagnostics);
      }
    }
  }

  private ObservedInvocation runEntryCommand(
      String commandName,
      String outputMode,
      Path bookFilePath,
      Path bookKeyFilePath,
      Path requestFile) {
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
    return new ObservedInvocation(
        exitCode,
        outputStream.toString(StandardCharsets.UTF_8),
        diagnosticsStream.toString(StandardCharsets.UTF_8));
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

  private static boolean hasAccountStateViolation(JsonNode rejectionEnvelope, String code) {
    return StreamSupport.stream(
            rejectionEnvelope.path("details").path("violations").spliterator(), false)
        .anyMatch(violation -> code.equals(violation.path("code").stringValue()));
  }

  private static String unknownAccountRequestJson() {
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
              "accountCode": "9998",
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
                "sourceDocumentId": "document-unknown-account",
                "sourceDocumentType": "working-note",
                "documentDate": "2026-04-07",
                "capturedAt": "2026-04-07T10:15:30Z",
                "storageLocator": "vault://fixtures/document-unknown-account",
                "contentSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
              }
            ],
            "approvals": []
          },
          "provenance": {
            "actorId": "actor-unknown-account",
            "actorType": "AGENT",
            "commandId": "command-unknown-account",
            "idempotencyKey": "idem-unknown-account",
            "causationId": "cause-unknown-account"
          }
        }
        """;
  }

  private record ObservedInvocation(int exitCode, String stdout, String stderr) {}
}
