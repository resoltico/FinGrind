package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Unit tests for evidence validation in {@link CliRequestReader}. */
class CliPostEntryRequestReaderEvidenceValidationTest extends CliRequestReaderTestSupport {

  @Test
  void readPostEntryCommand_rejectsMissingEvidenceObject() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "entryKind": "SALE_SETTLED",
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
                    .formatted(eurMoneyJson("1000"))
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Missing required field: evidence", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsNonObjectEvidenceField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "entryKind": "SALE_SETTLED",
                  "effectiveDate": "2026-04-07",
                  "cashAccountCode": "1000",
                  "revenueAccountCode": "2000",
                  "amount": %s,
                  "evidence": "not-an-object",
                  "provenance": {
                    "actorId": "actor-1",
                    "actorType": "AGENT",
                    "commandId": "command-1",
                    "idempotencyKey": "idem-1",
                    "causationId": "cause-1"
                  }
                }
                """
                    .formatted(eurMoneyJson("1000"))
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Field must be an object: evidence", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsMissingEvidenceSourceDocuments() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "entryKind": "SALE_SETTLED",
                  "effectiveDate": "2026-04-07",
                  "cashAccountCode": "1000",
                  "revenueAccountCode": "2000",
                  "amount": %s,
                  "evidence": {
                    "approvals": []
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
                    .formatted(eurMoneyJson("1000"))
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Missing required field: sourceDocuments", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsUnreplacedSourceDocumentIdPlaceholder() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                validRequestJson(false)
                    .replace("\"document-1\"", "\"replace-before-commit-source-document-id\"")
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals(
        "Scaffold placeholder must be replaced before submission: sourceDocuments[0].sourceDocumentId",
        exception.getMessage());
    assertEquals(CliJsonRequestHints.postEntryRequestHint(), exception.failure().hint());
  }
}
