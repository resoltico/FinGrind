package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.application.PostEntryCommand;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link CliRequestReader}. */
class CliRequestReaderTest {
  @TempDir Path tempDirectory;

  @Test
  void readPostEntryCommand_readsFromFile() throws IOException {
    Path requestFile = writeRequest(validRequestJson(true));
    CliRequestReader requestReader = new CliRequestReader(new ByteArrayInputStream(new byte[0]));

    PostEntryCommand command = requestReader.readPostEntryCommand(requestFile);

    assertEquals("idem-1", command.requestProvenance().idempotencyKey().value());
    assertEquals(
        Optional.of(new ReversalReference(new dev.erst.fingrind.core.PostingId("posting-0"))),
        command.reversalReference());
    assertEquals(
        Optional.of(new ReversalReason("operator reversal")), command.requestProvenance().reason());
    assertEquals(SourceChannel.CLI, command.sourceChannel());
  }

  @Test
  void readPostEntryCommand_readsFromStandardInputWithoutReversal() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(validRequestJson(false).getBytes(StandardCharsets.UTF_8)));

    PostEntryCommand command = requestReader.readPostEntryCommand(Path.of("-"));

    assertEquals(Optional.empty(), command.reversalReference());
    assertEquals(Optional.empty(), command.requestProvenance().reason());
    assertEquals(SourceChannel.CLI, command.sourceChannel());
  }

  @Test
  void readPostEntryCommand_treatsExplicitNullReversalAsEmpty() {
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
                  "reversal": null
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    PostEntryCommand command = requestReader.readPostEntryCommand(Path.of("-"));

    assertEquals(Optional.empty(), command.reversalReference());
  }

  @Test
  void readPostEntryCommand_rejectsNonObjectReversalField() {
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
                    "reason": "operator reversal"
                  },
                  "reversal": "posting-0"
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Field must be an object: reversal", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsLegacyCorrectionFieldEvenWhenNull() {
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
                  "correction": null
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Field is no longer accepted: correction", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsLegacyCorrectionFieldWhenPresent() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                validLegacyCorrectionRequestJson().getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Field is no longer accepted: correction", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsLegacyReversalKindFieldWhenPresent() {
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
                  "reversal": {
                    "kind": "REVERSAL",
                    "priorPostingId": "posting-0"
                  },
                  "provenance": {
                    "actorId": "actor-1",
                    "actorType": "AGENT",
                    "commandId": "command-1",
                    "idempotencyKey": "idem-1",
                    "causationId": "cause-1",
                    "reason": "operator reversal"
                  }
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Field is no longer accepted: kind", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsForbiddenRecordedAtField() {
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
                    "recordedAt": "2026-04-07T10:15:30Z"
                  }
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Field is no longer accepted: recordedAt", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsForbiddenRecordedAtFieldEvenWhenNull() {
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
                    "recordedAt": null
                  }
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Field is no longer accepted: recordedAt", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsForbiddenSourceChannelField() {
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
                    "sourceChannel": "CLI"
                  }
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Field is no longer accepted: sourceChannel", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsForbiddenSourceChannelFieldEvenWhenNull() {
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
                    "sourceChannel": null
                  }
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Field is no longer accepted: sourceChannel", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsMissingProvenanceObject() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "effectiveDate": "2026-04-07",
                  "lines": []
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Missing required field: provenance", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsNonObjectProvenanceField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "effectiveDate": "2026-04-07",
                  "lines": [],
                  "provenance": "not-an-object"
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Field must be an object: provenance", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsExplicitNullProvenanceField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "effectiveDate": "2026-04-07",
                  "lines": [],
                  "provenance": null
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Missing required field: provenance", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsMissingLinesField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "effectiveDate": "2026-04-07",
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

    assertEquals("Missing required field: lines", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsNonArrayLinesField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "effectiveDate": "2026-04-07",
                  "lines": "not-an-array",
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

    assertEquals("Field must be an array: lines", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsExplicitNullLinesField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "effectiveDate": "2026-04-07",
                  "lines": null,
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

    assertEquals("Missing required field: lines", exception.getMessage());
  }

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
                    "causationId": "cause-1",
                    "reason": 1
                  }
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Field must be a string when present: reason", exception.getMessage());
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
  void readPostEntryCommand_treatsExplicitNullOptionalTextFieldsAsEmpty() {
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
                    "correlationId": null,
                    "reason": null
                  }
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    PostEntryCommand command = requestReader.readPostEntryCommand(Path.of("-"));

    assertEquals(Optional.empty(), command.requestProvenance().correlationId());
    assertEquals(Optional.empty(), command.requestProvenance().reason());
  }

  private Path writeRequest(String payload) throws IOException {
    Path requestFile = tempDirectory.resolve("request.json");
    Files.writeString(requestFile, payload, StandardCharsets.UTF_8);
    return requestFile;
  }

  private static String validRequestJson(boolean includeReversal) {
    String reversalBlock =
        includeReversal
            ? """
              ,
              "reversal": {
                "priorPostingId": "posting-0"
              }
              """
            : "";
    String reasonField =
        includeReversal
            ? """
                "reason": "operator reversal",
              """
            : "";
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
                "causationId": "cause-1",
            %s    "correlationId": "corr-1"
              }
            %s
            }
            """
        .formatted(reasonField, reversalBlock);
  }

  private static String validLegacyCorrectionRequestJson() {
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
          "correction": {
            "kind": "AMENDMENT",
            "priorPostingId": "posting-0"
          },
          "provenance": {
            "actorId": "actor-1",
            "actorType": "AGENT",
            "commandId": "command-1",
            "idempotencyKey": "idem-1",
            "causationId": "cause-1",
            "reason": "operator correction"
          }
        }
        """;
  }
}
