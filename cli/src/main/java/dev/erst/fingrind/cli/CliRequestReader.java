package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliErrorJsonModels;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.core.JournalEntryValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
    return parseRequest(
        requestFile,
        CliJsonRequestHints.postEntryRequestHint(),
        OperationId.POST_ENTRY,
        CliPostingRequestParser::readPostEntryCommand);
  }

  /** Reads one account-declaration request from a JSON file or standard input. */
  DeclareAccountCommand readDeclareAccountCommand(Path requestFile) {
    return parseRequest(
        requestFile,
        CliJsonRequestHints.declareAccountRequestHint(),
        OperationId.DECLARE_ACCOUNT,
        CliPostingRequestParser::readDeclareAccountCommand);
  }

  /** Reads one AI-agent ledger plan from a JSON file or standard input. */
  LedgerPlan readLedgerPlan(Path requestFile) {
    return parseRequest(
        requestFile,
        CliJsonRequestHints.ledgerPlanRequestHint(),
        null,
        CliLedgerPlanParser::readLedgerPlan);
  }

  private <T> T parseRequest(
      Path requestFile,
      String requestHint,
      @org.jspecify.annotations.Nullable OperationId templateOperation,
      Function<tools.jackson.databind.node.ObjectNode, T> parser) {
    try {
      return parser.apply(
          CliJsonStructureAccess.requireRootObject(readRootNode(requestFile, requestHint)));
    } catch (CliRequestException exception) {
      throw exception;
    } catch (JournalEntryValidationException exception) {
      throw invalidRequestFromJournalValidation(exception, requestHint, templateOperation);
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw invalidRequestFromValueFailure(exception, requestHint, templateOperation);
    }
  }

  static CliRequestException invalidRequestFromJournalValidation(
      JournalEntryValidationException exception,
      String requestHint,
      @org.jspecify.annotations.Nullable OperationId templateOperation) {
    CliErrorJsonModels.InvalidRequestDetails details =
        new CliErrorJsonModels.InvalidRequestDetails(exception.violations());
    String message = CliJsonRequestFailures.normalizedMessage(exception);
    return new CliRequestException(
        ContractErrors.Descriptor.INVALID_REQUEST.code(),
        message,
        templateOperation == null
            ? CliRequestRepairHints.refineLedgerPlan(message, requestHint)
            : CliRequestRepairHints.refine(message, requestHint, details, templateOperation),
        exception,
        details);
  }

  static CliRequestException invalidRequestFromValueFailure(
      RuntimeException exception,
      String requestHint,
      @org.jspecify.annotations.Nullable OperationId templateOperation) {
    String message = CliJsonRequestFailures.normalizedMessage(exception);
    return new CliRequestException(
        ContractErrors.Descriptor.INVALID_REQUEST.code(),
        message,
        templateOperation == null
            ? CliRequestRepairHints.refineLedgerPlan(message, requestHint)
            : CliRequestRepairHints.refine(message, requestHint, null, templateOperation),
        exception);
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
    try (InputStream requestStream = Files.newInputStream(requestFile)) {
      return readBoundedBytes(requestStream);
    }
  }

  private static byte[] readBoundedBytes(InputStream stream) throws IOException {
    byte[] requestBytes =
        stream.readNBytes(ProtocolInteractionLimits.REQUEST_PAYLOAD_MAX_BYTES + 1);
    if (requestBytes.length > ProtocolInteractionLimits.REQUEST_PAYLOAD_MAX_BYTES) {
      throw new CliRequestPayloadTooLargeException(
          ProtocolInteractionLimits.REQUEST_PAYLOAD_MAX_BYTES);
    }
    return requestBytes;
  }
}
