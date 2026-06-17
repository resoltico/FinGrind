package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliRequestReader}. */
class CliPostEntryRequestReaderLegacyFieldValidationTest extends CliRequestReaderTestSupport {

  @Test
  void readPostEntryCommand_rejectsNonObjectReversalField() {
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
                  "reversal": "posting-0"
                }
                """
                            .formatted(standardBalancedLinesJson()))
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
                withEvidence(
                        """
                {
                  "entryKind": "JOURNAL",
                  "recipeKind": "CASH_REVENUE",
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
                  },
                  "correction": null
                }
                """
                            .formatted(eurMoneyJson("1000")))
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
                withEvidence(
                        """
                {
                  "entryKind": "REVERSAL_ADJUSTMENT",
                  "effectiveDate": "2026-04-07",
                  "lines": %s,
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
                            .formatted(standardBalancedLinesJson()))
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
                withEvidence(
                        """
                {
                  "entryKind": "JOURNAL",
                  "recipeKind": "CASH_REVENUE",
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
                    "recordedAt": "2026-04-07T10:15:30Z"
                  }
                }
                """
                            .formatted(eurMoneyJson("1000")))
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
                withEvidence(
                        """
                {
                  "entryKind": "JOURNAL",
                  "recipeKind": "CASH_REVENUE",
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
                    "recordedAt": null
                  }
                }
                """
                            .formatted(eurMoneyJson("1000")))
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
                withEvidence(
                        """
                {
                  "entryKind": "JOURNAL",
                  "recipeKind": "CASH_REVENUE",
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
                    "sourceChannel": "CLI"
                  }
                }
                """
                            .formatted(eurMoneyJson("1000")))
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
                withEvidence(
                        """
                {
                  "entryKind": "JOURNAL",
                  "recipeKind": "CASH_REVENUE",
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
                    "sourceChannel": null
                  }
                }
                """
                            .formatted(eurMoneyJson("1000")))
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Field is no longer accepted: sourceChannel", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsWrappedPostingPayloadWithoutTopLevelFields() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "posting": {
                    "entryKind": "JOURNAL",
                    "recipeKind": "CASH_REVENUE",
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
                }
                """
                    .formatted(eurMoneyJson("1000"))
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals(
        "Posting request fields must be top-level for direct request files; remove the posting wrapper.",
        exception.getMessage());
  }

  @Test
  void readPostEntryCommand_prefersUnexpectedFieldWhenTopLevelFieldsAndPostingWrapperAreMixed() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "JOURNAL",
                  "recipeKind": "CASH_REVENUE",
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
                  },
                  "posting": {
                    "entryKind": "JOURNAL",
                    "recipeKind": "CASH_REVENUE",
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
                }
                """
                            .formatted(eurMoneyJson("1000"), eurMoneyJson("1000")))
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Unexpected field: posting", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_reportsUnexpectedPostingWrapperWhenWrapperIsScalar() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "posting": "legacy-wrapper"
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Missing required field: entryKind", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_reportsUnexpectedPostingWrapperWhenWrapperIsNull() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "posting": null
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Missing required field: entryKind", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_reportsUnexpectedPostingWrapperWhenNestedShapeIsInvalid() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "posting": {
                    "entryKind": "JOURNAL",
                    "recipeKind": "CASH_REVENUE",
                    "effectiveDate": "2026-04-07",
                    "cashAccountCode": "1000",
                    "revenueAccountCode": "2000",
                    "amount": %s,
                    "ignored": true
                  }
                }
                """
                    .formatted(eurMoneyJson("1000"))
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Missing required field: entryKind", exception.getMessage());
  }
}
