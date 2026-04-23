package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliRequestReader}. */
class CliPostEntryRequestReaderValueValidationTest extends CliRequestReaderTestSupport {

  @Test
  void readPostEntryCommand_rejectsMissingRequiredTextField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
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
                      "side": "CREDIT",
                      "currencyCode": "EUR",
                      "amount": "10.00"
                    }
                  ],
                  "provenance": {
                    "actorType": "AGENT",
                    "commandId": "command-1",
                    "idempotencyKey": "idem-1",
                    "causationId": "cause-1"
                  }
                }
                """
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
                      "side": "CREDIT",
                      "currencyCode": "EUR",
                      "amount": "10.00"
                    }
                  ],
                  "provenance": {
                    "actorId": null,
                    "actorType": "AGENT",
                    "commandId": "command-1",
                    "idempotencyKey": "idem-1",
                    "causationId": "cause-1"
                  }
                }
                """
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
                """
                {
                  "effectiveDate": 20260407,
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
                """
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
                """
                {
                  "effectiveDate": "2026-02-30",
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
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Request contains an invalid date/time value.", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsExponentNotationAmounts() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "effectiveDate": "2026-04-07",
                  "lines": [
                    {
                      "accountCode": "1000",
                      "side": "DEBIT",
                      "currencyCode": "EUR",
                      "amount": "1e1000000100"
                    },
                    {
                      "accountCode": "2000",
                      "side": "CREDIT",
                      "currencyCode": "EUR",
                      "amount": "1.00"
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
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals(
        "Money amount must be a plain decimal string without exponent notation.",
        exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsUppercaseExponentNotationAmounts() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "effectiveDate": "2026-04-07",
                  "lines": [
                    {
                      "accountCode": "1000",
                      "side": "DEBIT",
                      "currencyCode": "EUR",
                      "amount": "1E6"
                    },
                    {
                      "accountCode": "2000",
                      "side": "CREDIT",
                      "currencyCode": "EUR",
                      "amount": "1.00"
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
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals(
        "Money amount must be a plain decimal string without exponent notation.",
        exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsNonDecimalAmounts() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "effectiveDate": "2026-04-07",
                  "lines": [
                    {
                      "accountCode": "1000",
                      "side": "DEBIT",
                      "currencyCode": "EUR",
                      "amount": "abc"
                    },
                    {
                      "accountCode": "2000",
                      "side": "CREDIT",
                      "currencyCode": "EUR",
                      "amount": "1.00"
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
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Money amount must be a valid decimal string.", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsWrongOptionalTextType() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
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
                  },
                  "reversal": {
                    "priorPostingId": "posting-0",
                    "reason": 1
                  }
                }
                """
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
                    "causationId": "cause-1",
                    "correlationId": 1
                  }
                }
                """
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

    assertEquals("Failed to read request JSON.", exception.getMessage());
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

    assertEquals("Failed to read request JSON.", exception.getMessage());
  }
}
