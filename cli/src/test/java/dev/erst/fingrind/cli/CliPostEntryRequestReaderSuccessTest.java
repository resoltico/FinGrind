package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CliRequestReader}. */
class CliPostEntryRequestReaderSuccessTest extends CliRequestReaderTestSupport {

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
                      "amount": %s
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
                  },
                  "reversal": null
                }
                """
                    .formatted(eurMoneyJson("1000"), eurMoneyJson("1000"))
                    .getBytes(StandardCharsets.UTF_8)));

    PostEntryCommand command = requestReader.readPostEntryCommand(Path.of("-"));

    assertEquals(Optional.empty(), command.reversalReference());
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
                      "amount": %s
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
                    "causationId": "cause-1",
                    "correlationId": null
                  }
                }
                """
                    .formatted(eurMoneyJson("1000"), eurMoneyJson("1000"))
                    .getBytes(StandardCharsets.UTF_8)));

    PostEntryCommand command = requestReader.readPostEntryCommand(Path.of("-"));

    assertEquals(Optional.empty(), command.requestProvenance().correlationId());
    assertEquals(Optional.empty(), command.reversalReason());
  }
}
