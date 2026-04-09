package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.application.PostEntryCommand;
import dev.erst.fingrind.core.CorrectionReference;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link CliRequestReader}. */
class CliRequestReaderTest {
  @TempDir Path tempDirectory;

  @Test
  void readPostEntryCommand_readsFromFile() throws IOException {
    Path requestFile = tempDirectory.resolve("request.json");
    Files.writeString(requestFile, validRequestJson(true, true), StandardCharsets.UTF_8);
    CliRequestReader requestReader = new CliRequestReader(new ByteArrayInputStream(new byte[0]));

    PostEntryCommand command =
        requestReader.readPostEntryCommand(
            requestFile, Clock.fixed(Instant.parse("2026-04-07T11:00:00Z"), ZoneOffset.UTC));

    assertEquals("idem-1", command.provenance().idempotencyKey().value());
    assertEquals(
        Optional.of(
            new CorrectionReference(
                CorrectionReference.CorrectionKind.AMENDMENT,
                command.journalEntry().correctionReference().orElseThrow().priorPostingId())),
        command.journalEntry().correctionReference());
    assertEquals(Instant.parse("2026-04-07T10:15:30Z"), command.provenance().recordedAt());
  }

  @Test
  void readPostEntryCommand_readsFromStandardInputAndDefaultsRecordedAt() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                validRequestJson(false, false).getBytes(StandardCharsets.UTF_8)));
    Clock fixedClock = Clock.fixed(Instant.parse("2026-04-07T12:00:00Z"), ZoneOffset.UTC);

    PostEntryCommand command = requestReader.readPostEntryCommand(Path.of("-"), fixedClock);

    assertEquals(Optional.empty(), command.journalEntry().correctionReference());
    assertEquals(Instant.parse("2026-04-07T12:00:00Z"), command.provenance().recordedAt());
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
                "causationId": "cause-1",
                "sourceChannel": "CLI"
              }
            }
            """
                    .getBytes(StandardCharsets.UTF_8)));

    assertThrows(
        IllegalArgumentException.class,
        () -> requestReader.readPostEntryCommand(Path.of("-"), Clock.systemUTC()));
  }

  @Test
  void readPostEntryCommand_rejectsWrongObjectFieldType() {
    Path requestFile =
        writeRequest(
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
              "provenance": "bad"
            }
            """);
    CliRequestReader requestReader = new CliRequestReader(new ByteArrayInputStream(new byte[0]));

    assertThrows(
        IllegalArgumentException.class,
        () -> requestReader.readPostEntryCommand(requestFile, Clock.systemUTC()));
  }

  @Test
  void readPostEntryCommand_rejectsWrongArrayFieldType() {
    Path requestFile =
        writeRequest(
            """
            {
              "effectiveDate": "2026-04-07",
              "lines": "bad",
              "provenance": {}
            }
            """);
    CliRequestReader requestReader = new CliRequestReader(new ByteArrayInputStream(new byte[0]));

    assertThrows(
        IllegalArgumentException.class,
        () -> requestReader.readPostEntryCommand(requestFile, Clock.systemUTC()));
  }

  @Test
  void readPostEntryCommand_rejectsWrongRequiredTextType() {
    Path requestFile =
        writeRequest(
            """
            {
              "effectiveDate": 1,
              "lines": [],
              "provenance": {}
            }
            """);
    CliRequestReader requestReader = new CliRequestReader(new ByteArrayInputStream(new byte[0]));

    assertThrows(
        IllegalArgumentException.class,
        () -> requestReader.readPostEntryCommand(requestFile, Clock.systemUTC()));
  }

  @Test
  void readPostEntryCommand_rejectsWrongOptionalTextType() {
    Path requestFile =
        writeRequest(
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
                "reason": 1,
                "sourceChannel": "CLI"
              }
            }
            """);
    CliRequestReader requestReader = new CliRequestReader(new ByteArrayInputStream(new byte[0]));

    assertThrows(
        IllegalArgumentException.class,
        () -> requestReader.readPostEntryCommand(requestFile, Clock.systemUTC()));
  }

  @Test
  void readPostEntryCommand_rejectsUnreadableRequestFile() {
    CliRequestReader requestReader = new CliRequestReader(new ByteArrayInputStream(new byte[0]));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            requestReader.readPostEntryCommand(
                tempDirectory.resolve("missing-request.json"), Clock.systemUTC()));
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

    assertThrows(
        IllegalArgumentException.class,
        () -> requestReader.readPostEntryCommand(Path.of("-"), Clock.systemUTC()));
  }

  @Test
  void readPostEntryCommand_rejectsInvalidEffectiveDate() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
            {
              "effectiveDate": "2026-04w-07",
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

    assertThrows(
        IllegalArgumentException.class,
        () -> requestReader.readPostEntryCommand(Path.of("-"), Clock.systemUTC()));
  }

  @Test
  void readPostEntryCommand_rejectsInvalidBinaryPayload() {
    CliRequestReader requestReader =
        new CliRequestReader(new ByteArrayInputStream(new byte[] {0x00, 0x00, 0x00, '{', 0x00}));

    assertThrows(
        IllegalArgumentException.class,
        () -> requestReader.readPostEntryCommand(Path.of("-"), Clock.systemUTC()));
  }

  @Test
  void readPostEntryCommand_treatsExplicitNullCorrectionAsEmpty() {
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
              "correction": null,
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

    PostEntryCommand command = requestReader.readPostEntryCommand(Path.of("-"), Clock.systemUTC());

    assertEquals(Optional.empty(), command.journalEntry().correctionReference());
  }

  @Test
  void readPostEntryCommand_rejectsMissingProvenanceObject() {
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
              ]
            }
            """
                    .getBytes(StandardCharsets.UTF_8)));

    assertThrows(
        IllegalArgumentException.class,
        () -> requestReader.readPostEntryCommand(Path.of("-"), Clock.systemUTC()));
  }

  @Test
  void readPostEntryCommand_rejectsNullProvenanceObject() {
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
              "provenance": null
            }
            """
                    .getBytes(StandardCharsets.UTF_8)));

    assertThrows(
        IllegalArgumentException.class,
        () -> requestReader.readPostEntryCommand(Path.of("-"), Clock.systemUTC()));
  }

  @Test
  void readPostEntryCommand_rejectsMissingLinesArray() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
            {
              "effectiveDate": "2026-04-07",
              "provenance": {}
            }
            """
                    .getBytes(StandardCharsets.UTF_8)));

    assertThrows(
        IllegalArgumentException.class,
        () -> requestReader.readPostEntryCommand(Path.of("-"), Clock.systemUTC()));
  }

  @Test
  void readPostEntryCommand_rejectsNullLinesArray() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
            {
              "effectiveDate": "2026-04-07",
              "lines": null,
              "provenance": {}
            }
            """
                    .getBytes(StandardCharsets.UTF_8)));

    assertThrows(
        IllegalArgumentException.class,
        () -> requestReader.readPostEntryCommand(Path.of("-"), Clock.systemUTC()));
  }

  @Test
  void readPostEntryCommand_rejectsNullRequiredTextField() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
            {
              "effectiveDate": null,
              "lines": [],
              "provenance": {}
            }
            """
                    .getBytes(StandardCharsets.UTF_8)));

    assertThrows(
        IllegalArgumentException.class,
        () -> requestReader.readPostEntryCommand(Path.of("-"), Clock.systemUTC()));
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
                "recordedAt": null,
                "reason": null,
                "sourceChannel": "CLI"
              }
            }
            """
                    .getBytes(StandardCharsets.UTF_8)));
    Clock fixedClock = Clock.fixed(Instant.parse("2026-04-07T13:00:00Z"), ZoneOffset.UTC);

    PostEntryCommand command = requestReader.readPostEntryCommand(Path.of("-"), fixedClock);

    assertEquals(Optional.empty(), command.provenance().correlationId());
    assertEquals(Optional.empty(), command.provenance().reason());
    assertEquals(Instant.parse("2026-04-07T13:00:00Z"), command.provenance().recordedAt());
  }

  private Path writeRequest(String payload) {
    try {
      Path requestFile = tempDirectory.resolve("request-" + System.nanoTime() + ".json");
      Files.writeString(requestFile, payload, StandardCharsets.UTF_8);
      return requestFile;
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static String validRequestJson(boolean includeCorrection, boolean includeRecordedAt) {
    String correctionBlock =
        includeCorrection
            ? """
              ,
              "correction": {
                "kind": "AMENDMENT",
                "priorPostingId": "posting-0"
              }
              """
            : "";
    String recordedAtField =
        includeRecordedAt
            ? """
                "recordedAt": "2026-04-07T10:15:30Z",
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
            %s    "sourceChannel": "CLI"
              }
            %s
            }
            """
        .formatted(recordedAtField, correctionBlock);
  }
}
