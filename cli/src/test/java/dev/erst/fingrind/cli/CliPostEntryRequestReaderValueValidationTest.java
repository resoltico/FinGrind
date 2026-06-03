package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliRequestReader}. */
class CliPostEntryRequestReaderValueValidationTest extends CliRequestReaderTestSupport {

  @Test
  void readPostEntryCommand_rejectsMissingRequiredTextField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "CASH_REVENUE",
                  "effectiveDate": "2026-04-07",
                  "cashAccountCode": "1000",
                  "revenueAccountCode": "2000",
                  "amount": %s,
                  "provenance": {
                    "actorType": "AGENT",
                    "commandId": "command-1",
                    "idempotencyKey": "idem-1",
                    "causationId": "cause-1"
                  }
                }
                """
                            .formatted(eurMoneyJson("1000")))
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Missing required field: actorId", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsExplicitNullRequiredTextField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "CASH_REVENUE",
                  "effectiveDate": "2026-04-07",
                  "cashAccountCode": "1000",
                  "revenueAccountCode": "2000",
                  "amount": %s,
                  "provenance": {
                    "actorId": null,
                    "actorType": "AGENT",
                    "commandId": "command-1",
                    "idempotencyKey": "idem-1",
                    "causationId": "cause-1"
                  }
                }
                """
                            .formatted(eurMoneyJson("1000")))
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Missing required field: actorId", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsNonStringRequiredTextField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "CASH_REVENUE",
                  "effectiveDate": 20260407,
                  "cashAccountCode": "1000",
                  "revenueAccountCode": "2000",
                  "amount": %s,
                  "provenance": {
                    "actorId": "actor-1",
                    "actorType": "AGENT",
                    "commandId": "command-1",
                    "idempotencyKey": "idem-1",
                    "causationId": "cause-1"
                  }
                }
                """
                            .formatted(eurMoneyJson("1000")))
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Field must be a string: effectiveDate", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsInvalidDateValue() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "CASH_REVENUE",
                  "effectiveDate": "2026-02-30",
                  "cashAccountCode": "1000",
                  "revenueAccountCode": "2000",
                  "amount": %s,
                  "provenance": {
                    "actorId": "actor-1",
                    "actorType": "AGENT",
                    "commandId": "command-1",
                    "idempotencyKey": "idem-1",
                    "causationId": "cause-1"
                  }
                }
                """
                            .formatted(eurMoneyJson("1000")))
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals(
        "Expected one canonical YYYY-MM-DD local date for effectiveDate.", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsExponentNotationAmounts() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "CASH_REVENUE",
                  "effectiveDate": "2026-04-07",
                  "cashAccountCode": "1000",
                  "revenueAccountCode": "2000",
                  "amount": %s,
                  "provenance": {
                    "actorId": "actor-1",
                    "actorType": "AGENT",
                    "commandId": "command-1",
                    "idempotencyKey": "idem-1",
                    "causationId": "cause-1"
                  }
                }
                """
                            .formatted(moneyJson("EUR", "1e1000000100")))
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("minorUnits must contain ASCII decimal digits only.", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsUppercaseExponentNotationAmounts() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "CASH_REVENUE",
                  "effectiveDate": "2026-04-07",
                  "cashAccountCode": "1000",
                  "revenueAccountCode": "2000",
                  "amount": %s,
                  "provenance": {
                    "actorId": "actor-1",
                    "actorType": "AGENT",
                    "commandId": "command-1",
                    "idempotencyKey": "idem-1",
                    "causationId": "cause-1"
                  }
                }
                """
                            .formatted(moneyJson("EUR", "1E6")))
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("minorUnits must contain ASCII decimal digits only.", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsNonDecimalAmounts() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "CASH_REVENUE",
                  "effectiveDate": "2026-04-07",
                  "cashAccountCode": "1000",
                  "revenueAccountCode": "2000",
                  "amount": %s,
                  "provenance": {
                    "actorId": "actor-1",
                    "actorType": "AGENT",
                    "commandId": "command-1",
                    "idempotencyKey": "idem-1",
                    "causationId": "cause-1"
                  }
                }
                """
                            .formatted(moneyJson("EUR", "abc")))
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("minorUnits must contain ASCII decimal digits only.", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_reportsEveryDetectedJournalGrammarViolationAtOnce() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "REVERSAL_ADJUSTMENT",
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
                                singleJournalLineJson("1000", "DEBIT", eurMoneyJson("1000"))))
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals(
        "Journal entry is invalid: Journal entry must contain at least one debit line and one credit line. Journal entry must balance debits and credits.",
        exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsWrongOptionalTextType() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "REVERSAL_ADJUSTMENT",
                  "effectiveDate": "2026-04-07",
                  "lines": %s,
                  "provenance": {
                    "actorId": "actor-1",
                    "actorType": "AGENT",
                    "commandId": "command-1",
                    "idempotencyKey": "idem-1",
                    "causationId": "cause-1"
                  },
                  "reversal": {
                    "priorPostingId": "posting-0",
                    "reason": 1
                  }
                }
                """
                            .formatted(standardBalancedLinesJson()))
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Field must be a string: reason", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsWrongOptionalProvenanceTextType() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "CASH_REVENUE",
                  "effectiveDate": "2026-04-07",
                  "cashAccountCode": "1000",
                  "revenueAccountCode": "2000",
                  "amount": %s,
                  "provenance": {
                    "actorId": "actor-1",
                    "actorType": "AGENT",
                    "commandId": "command-1",
                    "idempotencyKey": "idem-1",
                    "causationId": "cause-1",
                    "correlationId": 1
                  }
                }
                """
                            .formatted(eurMoneyJson("1000")))
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Field must be a string when present: correlationId", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsMalformedJsonSyntax() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "effectiveDate": "2026-04-07",
                  "lines", []
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Failed to read request JSON at line 3, column 10.", exception.getMessage());
    CliErrorJsonModels.InvalidJsonDetails details =
        assertInstanceOf(
            CliErrorJsonModels.InvalidJsonDetails.class, exception.failure().details());
    assertEquals(3, details.line());
    assertEquals(10, details.column());
  }

  @Test
  void readPostEntryCommand_handlesReadFailureWithNullExceptionMessage() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new InputStream() {
              @Override
              public int read() throws IOException {
                throw new IOException((String) null);
              }

              @Override
              public int read(byte[] destination, int offset, int length) throws IOException {
                throw new IOException((String) null);
              }
            });

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Failed to read request JSON from standard input.", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_preservesEveryJournalViolationInStructuredDetails() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "REVERSAL_ADJUSTMENT",
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
                                journalLinesJson(
                                    "1000",
                                    "DEBIT",
                                    eurMoneyJson("1000"),
                                    "2000",
                                    "DEBIT",
                                    moneyJson("USD", "500"))))
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    CliErrorJsonModels.InvalidRequestDetails details =
        assertInstanceOf(
            CliErrorJsonModels.InvalidRequestDetails.class, exception.failure().details());
    assertEquals(
        List.of(
            "Journal entry lines must share one currency.",
            "Journal entry must contain at least one debit line and one credit line.",
            "Journal entry must balance debits and credits."),
        details.violations());
  }
}
