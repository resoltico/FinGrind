package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.OperationId;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
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
                withEvidence(
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
                  },
                  "ignoredTopLevel": true
                }
                """
                            .formatted(eurMoneyJson("1000")))
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
                withEvidence(
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
                    "idempotencyKey": "idem-2",
                    "causationId": "cause-1"
                  }
                }
                """
                            .formatted(eurMoneyJson("1000")))
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

  @Test
  void readPostEntryCommand_pointsTypedRequestFailuresToTheirOwnScaffoldAndHelp() {
    CliRequestReader requestReader =
        new CliRequestReader(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class,
            () ->
                requestReader.readPostEntryCommand(
                    Path.of("-"), OperationId.RECORD_FINANCING_BORROWING));

    String hint = Objects.requireNonNull(exception.failure().hint());
    assertTrue(hint.contains("print-request-template record-financing-borrowing"), hint);
    assertTrue(hint.contains("help record-financing-borrowing --output json --detail full"), hint);
    assertTrue(!hint.contains("help post-entry --output json --detail full"), hint);
  }
}
