package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliRequestReader}. */
class CliPostEntryRequestReaderLineStructureValidationTest extends CliRequestReaderTestSupport {

  @Test
  void readPostEntryCommand_rejectsUnexpectedJournalLineField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "MANUAL_ADJUSTMENT",
                  "postingKind": "STANDARD",
                  "effectiveDate": "2026-04-07",
                  "lines": [
                    {
                      "accountCode": "1000",
                      "side": "DEBIT",
                      "amount": %s,
                      "ignoredByParser": "yes"
                    },
                    {
                      "accountCode": "2000",
                      "side": "CREDIT",
                      "amount": %s
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
                            .formatted(eurMoneyJson("1000"), eurMoneyJson("1000")))
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Unexpected field: lines[0].ignoredByParser", exception.getMessage());
  }

  @Test
  void readPostEntryCommand_rejectsMissingLinesField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                withEvidence(
                        """
                {
                  "entryKind": "MANUAL_ADJUSTMENT",
                  "postingKind": "STANDARD",
                  "effectiveDate": "2026-04-07",
                  "provenance": {
                    "actorId": "actor-1",
                    "actorType": "AGENT",
                    "commandId": "command-1",
                    "idempotencyKey": "idem-1",
                    "causationId": "cause-1"
                  }
                }
                """)
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
                withEvidence(
                        """
                {
                  "entryKind": "MANUAL_ADJUSTMENT",
                  "postingKind": "STANDARD",
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
                """)
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
                withEvidence(
                        """
                {
                  "entryKind": "MANUAL_ADJUSTMENT",
                  "postingKind": "STANDARD",
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
                """)
                    .getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class, () -> requestReader.readPostEntryCommand(Path.of("-")));

    assertEquals("Missing required field: lines", exception.getMessage());
  }
}
