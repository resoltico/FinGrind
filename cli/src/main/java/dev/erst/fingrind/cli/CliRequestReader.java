package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.contract.ContractErrors;
import dev.erst.fingrind.contract.DeclareAccountCommand;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.JournalEntryValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.util.Objects;
import java.util.function.Function;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.NullNode;

/** Parses FinGrind CLI request payloads into application commands. */
final class CliRequestReader {
  private final ObjectMapper objectMapper = CliJsonRequestCodec.configuredObjectMapper();
  private final InputStream inputStream;

  CliRequestReader(InputStream inputStream) {
    this.inputStream = Objects.requireNonNull(inputStream, "inputStream");
  }

  /** Reads one posting request from a JSON file or standard input. */
  PostEntryCommand readPostEntryCommand(Path requestFile) {
    return parseDatedRequest(
        requestFile,
        CliJsonRequestCodec.postEntryRequestHint(),
        "Request contains an invalid date/time value.",
        CliPostingRequestParser::readPostEntryCommand);
  }

  /** Reads one account-declaration request from a JSON file or standard input. */
  DeclareAccountCommand readDeclareAccountCommand(Path requestFile) {
    return parseRequest(
        requestFile,
        CliJsonRequestCodec.declareAccountRequestHint(),
        CliPostingRequestParser::readDeclareAccountCommand);
  }

  /** Reads one AI-agent ledger plan from a JSON file or standard input. */
  LedgerPlan readLedgerPlan(Path requestFile) {
    return parseDatedRequest(
        requestFile,
        CliJsonRequestCodec.ledgerPlanRequestHint(),
        "Ledger plan contains an invalid date/time value.",
        CliLedgerPlanParser::readLedgerPlan);
  }

  private <T> T parseRequest(
      Path requestFile,
      String requestHint,
      Function<tools.jackson.databind.node.ObjectNode, T> parser) {
    try {
      return parser.apply(
          CliJsonRequestCodec.requireRootObject(readRootNode(requestFile, requestHint)));
    } catch (CliRequestException exception) {
      throw exception;
    } catch (JournalEntryValidationException exception) {
      throw new CliRequestException(
          ContractErrors.Descriptor.INVALID_REQUEST.code(),
          CliJsonRequestCodec.normalizedMessage(exception),
          requestHint,
          exception,
          new CliErrorJsonModels.InvalidRequestDetails(exception.violations()));
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw new CliRequestException(
          ContractErrors.Descriptor.INVALID_REQUEST.code(),
          CliJsonRequestCodec.normalizedMessage(exception),
          requestHint,
          exception);
    }
  }

  private <T> T parseDatedRequest(
      Path requestFile,
      String requestHint,
      String invalidDateMessage,
      Function<tools.jackson.databind.node.ObjectNode, T> parser) {
    try {
      return parseRequest(requestFile, requestHint, parser);
    } catch (DateTimeException exception) {
      throw new CliRequestException(
          ContractErrors.Descriptor.INVALID_REQUEST.code(),
          invalidDateMessage,
          "Use ISO-8601 values such as YYYY-MM-DD and YYYY-MM-DDTHH:MM:SSZ.",
          exception);
    }
  }

  private JsonNode readRootNode(Path requestFile, String readFailureHint) {
    try {
      byte[] requestBytes = readRequestBytes(requestFile);
      if (CliJsonRequestCodec.hasDuplicateObjectKeys(requestBytes)) {
        throw CliJsonRequestCodec.duplicateObjectKeyFailure(readFailureHint);
      }
      return Objects.requireNonNullElseGet(
          objectMapper.readTree(requestBytes), NullNode::getInstance);
    } catch (IOException | JacksonException exception) {
      throw CliJsonRequestCodec.requestReadFailure(requestFile, exception, readFailureHint);
    }
  }

  private byte[] readRequestBytes(Path requestFile) throws IOException {
    if (ProtocolOptions.STDIN_TOKEN.equals(requestFile.toString())) {
      return inputStream.readAllBytes();
    }
    return Files.readAllBytes(requestFile);
  }
}
