package dev.erst.fingrind.cli;

import dev.erst.fingrind.application.PostEntryCommand;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrectionReason;
import dev.erst.fingrind.core.CorrectionReference;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Parses FinGrind CLI request payloads into application commands. */
final class CliRequestReader {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final InputStream inputStream;

  CliRequestReader(InputStream inputStream) {
    this.inputStream = Objects.requireNonNull(inputStream, "inputStream");
  }

  /** Reads one posting request from a JSON file or standard input. */
  PostEntryCommand readPostEntryCommand(Path requestFile) {
    try {
      JsonNode rootNode = readRootNode(requestFile);
      JsonNode provenanceNode = requiredObject(rootNode, "provenance");
      rejectForbiddenField(provenanceNode, "recordedAt");
      rejectForbiddenField(provenanceNode, "sourceChannel");
      return new PostEntryCommand(
          new JournalEntry(
              LocalDate.parse(requiredText(rootNode, "effectiveDate")),
              readLines(requiredArray(rootNode, "lines"))),
          readCorrection(rootNode.get("correction")),
          new RequestProvenance(
              new ActorId(requiredText(provenanceNode, "actorId")),
              ActorType.valueOf(requiredText(provenanceNode, "actorType")),
              new CommandId(requiredText(provenanceNode, "commandId")),
              new IdempotencyKey(requiredText(provenanceNode, "idempotencyKey")),
              new CausationId(requiredText(provenanceNode, "causationId")),
              optionalText(provenanceNode, "correlationId").map(CorrelationId::new),
              optionalText(provenanceNode, "reason").map(CorrectionReason::new)),
          SourceChannel.CLI);
    } catch (CliRequestException exception) {
      throw exception;
    } catch (java.time.DateTimeException exception) {
      throw new CliRequestException(
          "invalid-request",
          "Request contains an invalid date/time value.",
          "Use ISO-8601 values such as 2026-04-08 and 2026-04-08T12:00:00Z.",
          exception);
    } catch (IllegalArgumentException | ArithmeticException exception) {
      throw new CliRequestException(
          "invalid-request", normalizedMessage(exception), invalidRequestHint(), exception);
    }
  }

  private JsonNode readRootNode(Path requestFile) {
    try {
      if ("-".equals(requestFile.toString())) {
        return objectMapper.readTree(inputStream);
      }
      try (InputStream requestStream = Files.newInputStream(requestFile)) {
        return objectMapper.readTree(requestStream);
      }
    } catch (IOException | JacksonException exception) {
      throw new CliRequestException(
          "invalid-request",
          "Failed to read request JSON.",
          "Run 'fingrind print-request-template' for a minimal valid request document.",
          exception);
    }
  }

  private static String normalizedMessage(RuntimeException exception) {
    return Objects.requireNonNullElse(exception.getMessage(), "Request is invalid.");
  }

  private static String invalidRequestHint() {
    return "Run 'fingrind print-request-template' for a minimal valid request document, or"
        + " 'fingrind capabilities' for accepted enums and fields.";
  }

  private List<JournalLine> readLines(JsonNode linesNode) {
    List<JournalLine> lines = new java.util.ArrayList<>();
    for (JsonNode lineNode : linesNode) {
      lines.add(
          new JournalLine(
              new AccountCode(requiredText(lineNode, "accountCode")),
              JournalLine.EntrySide.valueOf(requiredText(lineNode, "side")),
              new Money(
                  new CurrencyCode(requiredText(lineNode, "currencyCode")),
                  parseAmount(requiredText(lineNode, "amount")))));
    }
    return lines;
  }

  private Optional<CorrectionReference> readCorrection(JsonNode correctionNode) {
    if (correctionNode == null || correctionNode.isNull()) {
      return Optional.empty();
    }
    return Optional.of(
        new CorrectionReference(
            CorrectionReference.CorrectionKind.valueOf(requiredText(correctionNode, "kind")),
            new PostingId(requiredText(correctionNode, "priorPostingId"))));
  }

  private static void rejectForbiddenField(JsonNode rootNode, String fieldName) {
    JsonNode fieldNode = rootNode.get(fieldName);
    if (fieldNode != null) {
      throw new IllegalArgumentException("Field is no longer accepted: " + fieldName);
    }
  }

  private static JsonNode requiredObject(JsonNode rootNode, String fieldName) {
    JsonNode fieldNode = rootNode.get(fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      throw new IllegalArgumentException("Missing required field: " + fieldName);
    }
    if (!fieldNode.isObject()) {
      throw new IllegalArgumentException("Field must be an object: " + fieldName);
    }
    return fieldNode;
  }

  private static JsonNode requiredArray(JsonNode rootNode, String fieldName) {
    JsonNode fieldNode = rootNode.get(fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      throw new IllegalArgumentException("Missing required field: " + fieldName);
    }
    if (!fieldNode.isArray()) {
      throw new IllegalArgumentException("Field must be an array: " + fieldName);
    }
    return fieldNode;
  }

  private static String requiredText(JsonNode rootNode, String fieldName) {
    JsonNode fieldNode = rootNode.get(fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      throw new IllegalArgumentException("Missing required field: " + fieldName);
    }
    if (!fieldNode.isTextual()) {
      throw new IllegalArgumentException("Field must be a string: " + fieldName);
    }
    return fieldNode.textValue();
  }

  private static Optional<String> optionalText(JsonNode rootNode, String fieldName) {
    JsonNode fieldNode = rootNode.get(fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      return Optional.empty();
    }
    if (!fieldNode.isTextual()) {
      throw new IllegalArgumentException("Field must be a string when present: " + fieldName);
    }
    return Optional.of(fieldNode.textValue());
  }

  private static BigDecimal parseAmount(String amountText) {
    if (amountText.indexOf('e') >= 0 || amountText.indexOf('E') >= 0) {
      throw new IllegalArgumentException(
          "Money amount must be a plain decimal string without exponent notation.");
    }
    try {
      return new BigDecimal(amountText);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Money amount must be a valid decimal string.", exception);
    }
  }
}
