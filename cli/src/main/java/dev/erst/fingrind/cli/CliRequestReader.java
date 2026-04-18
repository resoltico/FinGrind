package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.DeclareAccountCommand;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.PostEntryCommand;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.NullNode;

/** Parses FinGrind CLI request payloads into application commands. */
final class CliRequestReader {
  private final ObjectMapper objectMapper = CliJsonRequestSupport.configuredObjectMapper();
  private final InputStream inputStream;

  CliRequestReader(InputStream inputStream) {
    this.inputStream = Objects.requireNonNull(inputStream, "inputStream");
  }

  /** Reads one posting request from a JSON file or standard input. */
  PostEntryCommand readPostEntryCommand(Path requestFile) {
    try {
      return CliPostingRequestParser.readPostEntryCommand(
          CliJsonRequestSupport.requireRootObject(readRootNode(requestFile)));
    } catch (CliRequestException exception) {
      throw exception;
    } catch (java.time.DateTimeException exception) {
      throw new CliRequestException(
          "invalid-request",
          "Request contains an invalid date/time value.",
          "Use ISO-8601 values such as YYYY-MM-DD and YYYY-MM-DDTHH:MM:SSZ.",
          exception);
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw new CliRequestException(
          "invalid-request",
          CliJsonRequestSupport.normalizedMessage(exception),
          CliJsonRequestSupport.invalidRequestHint(),
          exception);
    }
  }

  /** Reads one account-declaration request from a JSON file or standard input. */
  DeclareAccountCommand readDeclareAccountCommand(Path requestFile) {
    try {
      return CliPostingRequestParser.readDeclareAccountCommand(
          CliJsonRequestSupport.requireRootObject(readRootNode(requestFile)));
    } catch (CliRequestException exception) {
      throw exception;
    } catch (IllegalArgumentException exception) {
      throw new CliRequestException(
          "invalid-request",
          CliJsonRequestSupport.normalizedMessage(exception),
          CliJsonRequestSupport.invalidRequestHint(),
          exception);
    }
  }

  /** Reads one AI-agent ledger plan from a JSON file or standard input. */
  LedgerPlan readLedgerPlan(Path requestFile) {
    try {
      return CliLedgerPlanParser.readLedgerPlan(
          CliJsonRequestSupport.requireRootObject(readRootNode(requestFile)));
    } catch (CliRequestException exception) {
      throw exception;
    } catch (java.time.DateTimeException exception) {
      throw new CliRequestException(
          "invalid-request",
          "Ledger plan contains an invalid date/time value.",
          "Use ISO-8601 values such as YYYY-MM-DD and YYYY-MM-DDTHH:MM:SSZ.",
          exception);
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw new CliRequestException(
          "invalid-request",
          CliJsonRequestSupport.normalizedMessage(exception),
          CliJsonRequestSupport.invalidRequestHint(),
          exception);
    }
  }

  private JsonNode readRootNode(Path requestFile) {
    try {
      if (ProtocolOptions.STDIN_TOKEN.equals(requestFile.toString())) {
        return Objects.requireNonNullElseGet(
            objectMapper.readTree(inputStream), NullNode::getInstance);
      }
      try (InputStream requestStream = Files.newInputStream(requestFile)) {
        return Objects.requireNonNullElseGet(
            objectMapper.readTree(requestStream), NullNode::getInstance);
      }
    } catch (IOException | JacksonException exception) {
      throw CliJsonRequestSupport.requestReadFailure(exception);
    }
  }
}
