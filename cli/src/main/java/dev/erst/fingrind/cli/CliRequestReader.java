package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.contract.ContractErrors;
import dev.erst.fingrind.contract.DeclareAccountCommand;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.InteractionLimits;
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
  private final ObjectMapper objectMapper = CliJsonObjectMappers.configuredObjectMapper();
  private final InputStream inputStream;

  CliRequestReader(InputStream inputStream) {
    this.inputStream = Objects.requireNonNull(inputStream, "inputStream");
  }

  /** Reads one posting request from a JSON file or standard input. */
  PostEntryCommand readPostEntryCommand(Path requestFile) {
    return parseDatedRequest(
        requestFile,
        CliJsonRequestHints.postEntryRequestHint(),
        "Request contains an invalid date/time value.",
        CliPostingRequestParser::readPostEntryCommand);
  }

  /** Reads one account-declaration request from a JSON file or standard input. */
  DeclareAccountCommand readDeclareAccountCommand(Path requestFile) {
    return parseRequest(
        requestFile,
        CliJsonRequestHints.declareAccountRequestHint(),
        CliPostingRequestParser::readDeclareAccountCommand);
  }

  /** Reads one AI-agent ledger plan from a JSON file or standard input. */
  LedgerPlan readLedgerPlan(Path requestFile) {
    return parseDatedRequest(
        requestFile,
        CliJsonRequestHints.ledgerPlanRequestHint(),
        "Ledger plan contains an invalid date/time value.",
        CliLedgerPlanParser::readLedgerPlan);
  }

  private <T> T parseRequest(
      Path requestFile,
      String requestHint,
      Function<tools.jackson.databind.node.ObjectNode, T> parser) {
    try {
      return parser.apply(
          CliJsonFieldAccess.requireRootObject(readRootNode(requestFile, requestHint)));
    } catch (CliRequestException exception) {
      throw exception;
    } catch (JournalEntryValidationException exception) {
      throw new CliRequestException(
          ContractErrors.Descriptor.INVALID_REQUEST.code(),
          CliJsonRequestFailures.normalizedMessage(exception),
          requestHint,
          exception,
          new CliErrorJsonModels.InvalidRequestDetails(exception.violations()));
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw new CliRequestException(
          ContractErrors.Descriptor.INVALID_REQUEST.code(),
          CliJsonRequestFailures.normalizedMessage(exception),
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
    byte[] requestBytes;
    try {
      requestBytes = readRequestBytes(requestFile);
    } catch (IOException exception) {
      throw CliJsonRequestFailures.requestReadFailure(requestFile, exception, readFailureHint);
    }
    if (CliJsonObjectMappers.hasDuplicateObjectKeys(requestBytes)) {
      throw CliJsonRequestFailures.duplicateObjectKeyFailure(readFailureHint);
    }
    try {
      return Objects.requireNonNullElseGet(
          objectMapper.readTree(requestBytes), NullNode::getInstance);
    } catch (JacksonException exception) {
      throw CliJsonRequestFailures.requestReadFailure(requestFile, exception, readFailureHint);
    }
  }

  private byte[] readRequestBytes(Path requestFile) throws IOException {
    if (ProtocolOptions.STDIN_TOKEN.equals(requestFile.toString())) {
      return readBoundedBytes(inputStream);
    }
    try (InputStream fileStream = Files.newInputStream(requestFile)) {
      return readBoundedBytes(fileStream);
    }
  }

  private static byte[] readBoundedBytes(InputStream stream) throws IOException {
    byte[] requestBytes = stream.readNBytes(InteractionLimits.REQUEST_PAYLOAD_MAX_BYTES + 1);
    if (requestBytes.length > InteractionLimits.REQUEST_PAYLOAD_MAX_BYTES) {
      throw new CliRequestPayloadTooLargeException(InteractionLimits.REQUEST_PAYLOAD_MAX_BYTES);
    }
    return requestBytes;
  }
}
