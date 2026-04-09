package dev.erst.fingrind.cli;

import dev.erst.fingrind.application.PostEntryCommand;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CorrectionReference;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ProvenanceEnvelope;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
  PostEntryCommand readPostEntryCommand(Path requestFile, Clock clock) {
    try {
      JsonNode rootNode = readRootNode(requestFile);
      return new PostEntryCommand(
          new JournalEntry(
              LocalDate.parse(requiredText(rootNode, "effectiveDate")),
              readLines(requiredArray(rootNode, "lines")),
              readCorrection(rootNode.get("correction"))),
          new ProvenanceEnvelope(
              requiredText(requiredObject(rootNode, "provenance"), "actorId"),
              ProvenanceEnvelope.ActorType.valueOf(
                  requiredText(requiredObject(rootNode, "provenance"), "actorType")),
              requiredText(requiredObject(rootNode, "provenance"), "commandId"),
              new IdempotencyKey(
                  requiredText(requiredObject(rootNode, "provenance"), "idempotencyKey")),
              requiredText(requiredObject(rootNode, "provenance"), "causationId"),
              optionalText(requiredObject(rootNode, "provenance"), "correlationId"),
              readRecordedAt(requiredObject(rootNode, "provenance"), clock),
              optionalText(requiredObject(rootNode, "provenance"), "reason"),
              ProvenanceEnvelope.SourceChannel.valueOf(
                  requiredText(requiredObject(rootNode, "provenance"), "sourceChannel"))));
    } catch (CliRequestException exception) {
      throw exception;
    } catch (DateTimeException exception) {
      throw new CliRequestException(
          "invalid-request",
          "Request contains an invalid date/time value.",
          "Use ISO-8601 values such as 2026-04-08 and 2026-04-08T12:00:00Z.",
          exception);
    } catch (IllegalArgumentException exception) {
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

  private static String normalizedMessage(IllegalArgumentException exception) {
    return Objects.requireNonNullElse(exception.getMessage(), "Request is invalid.");
  }

  private static String invalidRequestHint() {
    return "Run 'fingrind print-request-template' for a minimal valid request document, or"
        + " 'fingrind capabilities' for accepted enums and fields.";
  }

  private List<JournalLine> readLines(JsonNode linesNode) {
    List<JournalLine> lines = new ArrayList<>();
    for (JsonNode lineNode : linesNode) {
      lines.add(
          new JournalLine(
              new AccountCode(requiredText(lineNode, "accountCode")),
              JournalLine.EntrySide.valueOf(requiredText(lineNode, "side")),
              new Money(
                  new CurrencyCode(requiredText(lineNode, "currencyCode")),
                  new BigDecimal(requiredText(lineNode, "amount")))));
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

  private Instant readRecordedAt(JsonNode provenanceNode, Clock clock) {
    Optional<String> recordedAt = optionalText(provenanceNode, "recordedAt");
    return recordedAt.map(Instant::parse).orElseGet(clock::instant);
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
}
