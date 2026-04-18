package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.DeclareAccountCommand;
import dev.erst.fingrind.contract.LedgerAssertion;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerStep;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.PostingPageCursor;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
  void readDeclareAccountCommand_readsFromFile() throws IOException {
    Path requestFile =
        writeNamedRequest(
            "declare-account.json",
            """
            {
              "accountCode": "1000",
              "accountName": "Cash",
              "normalBalance": "DEBIT"
            }
            """);
    CliRequestReader requestReader = new CliRequestReader(new ByteArrayInputStream(new byte[0]));

    DeclareAccountCommand command = requestReader.readDeclareAccountCommand(requestFile);

    assertEquals("1000", command.accountCode().value());
    assertEquals("Cash", command.accountName().value());
    assertEquals("DEBIT", command.normalBalance().name());
  }

  @Test
  void readDeclareAccountCommand_rejectsMissingRequiredField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "accountCode": "1000",
                  "normalBalance": "DEBIT"
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readDeclareAccountCommand(Path.of("-")));

    assertEquals("Missing required field: accountName", exception.getMessage());
  }

  @Test
  void readDeclareAccountCommand_rejectsInvalidNormalBalance() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "accountCode": "1000",
                  "accountName": "Cash",
                  "normalBalance": "SIDEWAYS"
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readDeclareAccountCommand(Path.of("-")));

    assertEquals(
        "Unsupported value for normalBalance: SIDEWAYS. Accepted values: DEBIT, CREDIT.",
        exception.getMessage());
  }

  @Test
  void readDeclareAccountCommand_rejectsUnexpectedTopLevelField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "accountCode": "1000",
                  "accountName": "Cash",
                  "normalBalance": "DEBIT",
                  "ignored": true
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readDeclareAccountCommand(Path.of("-")));

    assertEquals("Unexpected field: ignored", exception.getMessage());
  }

  @Test
  void readDeclareAccountCommand_reportsEveryUnexpectedTopLevelFieldTogether() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "accountCode": "1000",
                  "accountName": "Cash",
                  "normalBalance": "DEBIT",
                  "ignored": true,
                  "alsoIgnored": true
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readDeclareAccountCommand(Path.of("-")));

    assertEquals("Unexpected fields: ignored, alsoIgnored", exception.getMessage());
  }

  @Test
  void readDeclareAccountCommand_rejectsNonObjectRootDocument() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                [
                  {
                    "accountCode": "1000",
                    "accountName": "Cash",
                    "normalBalance": "DEBIT"
                  }
                ]
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readDeclareAccountCommand(Path.of("-")));

    assertEquals("Request JSON document must be an object.", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_readsFromFile() throws IOException {
    Path requestFile = writeRequest(validRequestJson(true));
    CliRequestReader requestReader = new CliRequestReader(new ByteArrayInputStream(new byte[0]));

    PostEntryCommand command = requestReader.readPostEntryCommand(requestFile);

    assertEquals("idem-1", command.requestProvenance().idempotencyKey().value());
    assertEquals(
        Optional.of(new ReversalReference(new dev.erst.fingrind.core.PostingId("posting-0"))),
        command.reversalReference());
    assertEquals(Optional.of(new ReversalReason("operator reversal")), command.reversalReason());
    assertEquals(SourceChannel.CLI, command.sourceChannel());
  }

  @Test
  void readPostEntryCommand_readsFromStandardInputWithoutReversal() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(validRequestJson(false).getBytes(StandardCharsets.UTF_8)));

    PostEntryCommand command = requestReader.readPostEntryCommand(Path.of("-"));

    assertEquals(Optional.empty(), command.reversalReference());
    assertEquals(Optional.empty(), command.reversalReason());
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
                    "causationId": "cause-1"
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
  void readPostEntryCommand_rejectsUnexpectedTopLevelField() {
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
                  "ignoredTopLevel": true
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Unexpected field: ignoredTopLevel", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsUnexpectedJournalLineField() {
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
                      "amount": "10.00",
                      "ignoredByParser": "yes"
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

    assertEquals("Unexpected field: lines[0].ignoredByParser", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsDuplicateObjectKeys() {
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
                    "idempotencyKey": "idem-2",
                    "causationId": "cause-1"
                  }
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals(
        "Request JSON must not contain duplicate object keys. Duplicate key: idempotencyKey",
        exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsNonObjectRootDocument() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                [
                  {
                    "effectiveDate": "2026-04-07"
                  }
                ]
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Request JSON document must be an object.", exception.getMessage());
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
                    "causationId": "cause-1"
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
  void readPostEntryCommand_rejectsEmptyInputDocument() {
    CliRequestReader requestReader = new CliRequestReader(new ByteArrayInputStream(new byte[0]));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Request JSON document must be an object.", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsExplicitNullRootDocument() {
    CliRequestReader requestReader =
        new CliRequestReader(new ByteArrayInputStream("null".getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Request JSON document must be an object.", exception.getMessage());
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
                    "correlationId": null
                  }
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    PostEntryCommand command = requestReader.readPostEntryCommand(Path.of("-"));

    assertEquals(Optional.empty(), command.requestProvenance().correlationId());
    assertEquals(Optional.empty(), command.reversalReason());
  }

  @Test
  void readLedgerPlan_readsEverySupportedStepKindFromStandardInput() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(validLedgerPlanJson().getBytes(StandardCharsets.UTF_8)));

    LedgerPlan plan = requestReader.readLedgerPlan(Path.of("-"));

    assertEquals("plan-1", plan.planId().value());
    assertEquals(13, plan.steps().size());
    assertEquals(LedgerStep.OpenBook.class, plan.steps().get(0).getClass());
    assertEquals(LedgerStep.DeclareAccount.class, plan.steps().get(1).getClass());
    assertEquals(LedgerStep.PreflightEntry.class, plan.steps().get(2).getClass());
    assertEquals(LedgerStep.PostEntry.class, plan.steps().get(3).getClass());
    assertEquals(LedgerStep.InspectBook.class, plan.steps().get(4).getClass());
    assertEquals(LedgerStep.ListAccounts.class, plan.steps().get(5).getClass());
    assertEquals(LedgerStep.GetPosting.class, plan.steps().get(6).getClass());
    assertEquals(LedgerStep.ListPostings.class, plan.steps().get(7).getClass());
    assertEquals(LedgerStep.AccountBalance.class, plan.steps().get(8).getClass());
    assertEquals(LedgerAssertion.AccountDeclared.class, assertionAt(plan, 9).getClass());
    assertEquals(LedgerAssertion.AccountActive.class, assertionAt(plan, 10).getClass());
    assertEquals(LedgerAssertion.PostingExists.class, assertionAt(plan, 11).getClass());
    assertEquals(LedgerAssertion.AccountBalanceEquals.class, assertionAt(plan, 12).getClass());
  }

  @Test
  void readLedgerPlan_defaultsOptionalQueryObjects() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "planId": "plan-1",
                  "steps": [
                    {
                      "stepId": "list-accounts",
                      "kind": "list-accounts"
                    },
                    {
                      "stepId": "list-postings",
                      "kind": "list-postings"
                    }
                  ]
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    LedgerPlan plan = requestReader.readLedgerPlan(Path.of("-"));

    assertEquals(50, ((LedgerStep.ListAccounts) plan.steps().get(0)).query().limit());
    assertTrue(((LedgerStep.ListPostings) plan.steps().get(1)).query().cursor().isEmpty());
  }

  @Test
  void readLedgerPlan_rejectsExecutionPolicyBecauseExecutionSemanticsAreCoreOwned() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "planId": "plan-1",
                  "executionPolicy": {
                    "journalLevel": "VERBOSE"
                  },
                  "steps": [
                    {
                      "stepId": "inspect",
                      "kind": "inspect-book"
                    }
                  ]
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(CliRequestException.class, () -> requestReader.readLedgerPlan(Path.of("-")));

    assertEquals("Unexpected field: executionPolicy", exception.getMessage());
  }

  @Test
  void readLedgerPlan_rethrowsJsonReadFailures() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "planId": "plan-1",
                  "planId": "plan-2",
                  "steps": []
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(CliRequestException.class, () -> requestReader.readLedgerPlan(Path.of("-")));

    assertEquals(
        "Request JSON must not contain duplicate object keys. Duplicate key: planId",
        exception.getMessage());
  }

  @Test
  void readLedgerPlan_reportsInvalidDateValues() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                validLedgerPlanJson()
                    .replace(
                        "\"effectiveDateFrom\": \"2026-04-01\"",
                        "\"effectiveDateFrom\": \"2026-02-30\"")
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(CliRequestException.class, () -> requestReader.readLedgerPlan(Path.of("-")));

    assertEquals("Ledger plan contains an invalid date/time value.", exception.getMessage());
  }

  @Test
  void readLedgerPlan_reportsInvalidShapeValues() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                validLedgerPlanJson()
                    .replace("\"kind\": \"open-book\"", "\"kind\": \"unsupported-step\"")
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(CliRequestException.class, () -> requestReader.readLedgerPlan(Path.of("-")));

    assertTrue(exception.getMessage().startsWith("Unsupported value for kind: unsupported-step."));
  }

  @Test
  void readLedgerPlan_rejectsWrongOptionalIntegerType() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                validLedgerPlanJson()
                    .replace("\"limit\": 25", "\"limit\": \"25\"")
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(CliRequestException.class, () -> requestReader.readLedgerPlan(Path.of("-")));

    assertEquals("Field must be an integer when present: limit", exception.getMessage());
  }

  @Test
  void readLedgerPlan_rejectsUnsupportedAssertionKind() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                validLedgerPlanJson()
                    .replace(
                        "\"kind\": \"assert-account-balance\"", "\"kind\": \"assert-sideways\"")
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(CliRequestException.class, () -> requestReader.readLedgerPlan(Path.of("-")));

    assertTrue(
        exception
            .getMessage()
            .startsWith("Unsupported value for assertion.kind: assert-sideways."));
  }

  @Test
  void readLedgerPlan_treatsExplicitNullOptionalQueryFieldsAsMissing() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "planId": "plan-1",
                  "steps": [
                    {
                      "stepId": "list-accounts",
                      "kind": "list-accounts",
                      "query": {
                        "offset": null
                      }
                    },
                    {
                      "stepId": "list-postings",
                      "kind": "list-postings",
                      "query": null
                    }
                  ]
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    LedgerPlan plan = requestReader.readLedgerPlan(Path.of("-"));

    assertEquals(50, ((LedgerStep.ListAccounts) plan.steps().get(0)).query().limit());
    assertTrue(((LedgerStep.ListPostings) plan.steps().get(1)).query().cursor().isEmpty());
  }

  private Path writeRequest(String payload) throws IOException {
    return writeNamedRequest("request.json", payload);
  }

  private Path writeNamedRequest(String fileName, String payload) throws IOException {
    Path requestFile = tempDirectory.resolve(fileName);
    Files.writeString(requestFile, payload, StandardCharsets.UTF_8);
    return requestFile;
  }

  private static String validRequestJson(boolean includeReversal) {
    String reversalBlock =
        includeReversal
            ? """
                ,
                "reversal": {
                  "priorPostingId": "posting-0",
                  "reason": "operator reversal"
                }
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
                "correlationId": "corr-1"
              }
            %s
            }
            """
        .formatted(reversalBlock);
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

  private static LedgerAssertion assertionAt(LedgerPlan plan, int index) {
    return ((LedgerStep.Assert) plan.steps().get(index)).assertion();
  }

  private static String validLedgerPlanJson() {
    return """
        {
          "planId": "plan-1",
          "steps": [
            {
              "stepId": "open",
              "kind": "open-book"
            },
            {
              "stepId": "declare",
              "kind": "declare-account",
              "declareAccount": {
                "accountCode": "1000",
                "accountName": "Cash",
                "normalBalance": "DEBIT"
              }
            },
            {
              "stepId": "preflight",
              "kind": "preflight-entry",
              "posting": %s
            },
            {
              "stepId": "post",
              "kind": "post-entry",
              "posting": %s
            },
            {
              "stepId": "inspect",
              "kind": "inspect-book"
            },
            {
              "stepId": "list-accounts",
              "kind": "list-accounts",
              "query": {
                "limit": 25,
                "offset": 5
              }
            },
            {
              "stepId": "get-posting",
              "kind": "get-posting",
              "postingId": "posting-1"
            },
            {
              "stepId": "list-postings",
              "kind": "list-postings",
              "query": {
                "accountCode": "1000",
                "effectiveDateFrom": "2026-04-01",
                "effectiveDateTo": "2026-04-30",
                "limit": 25,
                "cursor": "%s"
              }
            },
            {
              "stepId": "account-balance",
              "kind": "account-balance",
              "query": {
                "accountCode": "1000",
                "effectiveDateFrom": "2026-04-01",
                "effectiveDateTo": "2026-04-30"
              }
            },
            {
              "stepId": "assert-declared",
              "kind": "assert",
              "assertion": {
                "kind": "assert-account-declared",
                "accountCode": "1000"
              }
            },
            {
              "stepId": "assert-active",
              "kind": "assert",
              "assertion": {
                "kind": "assert-account-active",
                "accountCode": "1000"
              }
            },
            {
              "stepId": "assert-posting",
              "kind": "assert",
              "assertion": {
                "kind": "assert-posting-exists",
                "postingId": "posting-1"
              }
            },
            {
              "stepId": "assert-balance",
              "kind": "assert",
              "assertion": {
                "kind": "assert-account-balance",
                "accountCode": "1000",
                "effectiveDateFrom": "2026-04-01",
                "effectiveDateTo": "2026-04-30",
                "currencyCode": "EUR",
                "netAmount": "10.00",
                "balanceSide": "DEBIT"
              }
            }
          ]
        }
        """
        .formatted(validRequestJson(false), validRequestJson(false), validPostingCursor());
  }

  private static String validPostingCursor() {
    return new PostingPageCursor(
            java.time.LocalDate.parse("2026-04-15"),
            java.time.Instant.parse("2026-04-15T10:15:30Z"),
            new PostingId("posting-5"))
        .wireValue();
  }
}
