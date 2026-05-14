package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliRequestReader}. */
class CliPostEntryRequestReaderRootValidationTest extends CliRequestReaderTestSupport {

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
  void readPostEntryCommand_rejectsUnexpectedTopLevelField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "postingKind": "STANDARD",
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
                      "accountCode": "2000",
                      "side": "CREDIT",
                      "amount": {
                        "currencyCode": "EUR",
                        "minorUnits": "1000"
                      }
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
  void readPostEntryCommand_rejectsDuplicateObjectKeys() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "postingKind": "STANDARD",
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
                      "accountCode": "2000",
                      "side": "CREDIT",
                      "amount": {
                        "currencyCode": "EUR",
                        "minorUnits": "1000"
                      }
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

    assertEquals("Request JSON must not contain duplicate object keys.", exception.getMessage());
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
}
