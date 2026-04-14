package dev.erst.fingrind.cli;

import dev.erst.fingrind.application.DeclareAccountCommand;
import dev.erst.fingrind.application.MachineContract;
import dev.erst.fingrind.application.PostEntryCommand;
import dev.erst.fingrind.core.*;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

/** Parses FinGrind CLI request payloads into application commands. */
final class CliRequestReader {
  private static final Pattern DUPLICATE_FIELD_PATTERN =
      Pattern.compile("(?i)Duplicate[^'\"]*['\"]([^'\"]+)['\"]");
  private static final String ROOT_DOCUMENT_MUST_BE_OBJECT =
      "Request JSON document must be an object.";
  private static final Set<String> DECLARE_ACCOUNT_FIELDS =
      Set.of(
          MachineContract.DeclareAccountFields.ACCOUNT_CODE,
          MachineContract.DeclareAccountFields.ACCOUNT_NAME,
          MachineContract.DeclareAccountFields.NORMAL_BALANCE);
  private static final Set<String> POST_ENTRY_TOP_LEVEL_FIELDS =
      Set.of(
          MachineContract.PostEntryTopLevelFields.EFFECTIVE_DATE,
          MachineContract.PostEntryTopLevelFields.LINES,
          MachineContract.PostEntryTopLevelFields.PROVENANCE,
          MachineContract.PostEntryTopLevelFields.REVERSAL);
  private static final Set<String> PROVENANCE_FIELDS =
      Set.of(
          MachineContract.ProvenanceFields.ACTOR_ID,
          MachineContract.ProvenanceFields.ACTOR_TYPE,
          MachineContract.ProvenanceFields.COMMAND_ID,
          MachineContract.ProvenanceFields.IDEMPOTENCY_KEY,
          MachineContract.ProvenanceFields.CAUSATION_ID,
          MachineContract.ProvenanceFields.CORRELATION_ID,
          MachineContract.ProvenanceFields.REASON);
  private static final Set<String> JOURNAL_LINE_FIELDS =
      Set.of(
          MachineContract.JournalLineFields.ACCOUNT_CODE,
          MachineContract.JournalLineFields.SIDE,
          MachineContract.JournalLineFields.CURRENCY_CODE,
          MachineContract.JournalLineFields.AMOUNT);
  private static final Set<String> REVERSAL_FIELDS =
      Set.of(MachineContract.ReversalFields.PRIOR_POSTING_ID);

  private final ObjectMapper objectMapper = configuredObjectMapper();
  private final InputStream inputStream;

  CliRequestReader(InputStream inputStream) {
    this.inputStream = Objects.requireNonNull(inputStream, "inputStream");
  }

  /** Reads one posting request from a JSON file or standard input. */
  PostEntryCommand readPostEntryCommand(Path requestFile) {
    try {
      ObjectNode rootNode = requireRootObject(readRootNode(requestFile));
      rejectForbiddenField(rootNode, MachineContract.PostEntryTopLevelFields.CORRECTION);
      rejectUnexpectedFields(rootNode, null, POST_ENTRY_TOP_LEVEL_FIELDS);
      ObjectNode provenanceNode =
          requiredObject(rootNode, MachineContract.PostEntryTopLevelFields.PROVENANCE);
      rejectForbiddenField(provenanceNode, MachineContract.ProvenanceFields.RECORDED_AT);
      rejectForbiddenField(provenanceNode, MachineContract.ProvenanceFields.SOURCE_CHANNEL);
      rejectUnexpectedFields(
          provenanceNode, MachineContract.PostEntryTopLevelFields.PROVENANCE, PROVENANCE_FIELDS);
      return new PostEntryCommand(
          new JournalEntry(
              LocalDate.parse(
                  requiredText(rootNode, MachineContract.PostEntryTopLevelFields.EFFECTIVE_DATE)),
              readLines(requiredArray(rootNode, MachineContract.PostEntryTopLevelFields.LINES))),
          readReversal(rootNode.get(MachineContract.PostEntryTopLevelFields.REVERSAL)),
          new RequestProvenance(
              new ActorId(requiredText(provenanceNode, MachineContract.ProvenanceFields.ACTOR_ID)),
              dev.erst.fingrind.core.ActorType.valueOf(
                  requiredText(provenanceNode, MachineContract.ProvenanceFields.ACTOR_TYPE)),
              new CommandId(
                  requiredText(provenanceNode, MachineContract.ProvenanceFields.COMMAND_ID)),
              new IdempotencyKey(
                  requiredText(provenanceNode, MachineContract.ProvenanceFields.IDEMPOTENCY_KEY)),
              new CausationId(
                  requiredText(provenanceNode, MachineContract.ProvenanceFields.CAUSATION_ID)),
              optionalText(provenanceNode, MachineContract.ProvenanceFields.CORRELATION_ID)
                  .map(CorrelationId::new),
              optionalText(provenanceNode, MachineContract.ProvenanceFields.REASON)
                  .map(ReversalReason::new)),
          dev.erst.fingrind.core.SourceChannel.CLI);
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

  /** Reads one account-declaration request from a JSON file or standard input. */
  DeclareAccountCommand readDeclareAccountCommand(Path requestFile) {
    try {
      ObjectNode rootNode = requireRootObject(readRootNode(requestFile));
      rejectUnexpectedFields(rootNode, null, DECLARE_ACCOUNT_FIELDS);
      return new DeclareAccountCommand(
          new AccountCode(
              requiredText(rootNode, MachineContract.DeclareAccountFields.ACCOUNT_CODE)),
          new AccountName(
              requiredText(rootNode, MachineContract.DeclareAccountFields.ACCOUNT_NAME)),
          dev.erst.fingrind.core.NormalBalance.valueOf(
              requiredText(rootNode, MachineContract.DeclareAccountFields.NORMAL_BALANCE)));
    } catch (CliRequestException exception) {
      throw exception;
    } catch (IllegalArgumentException exception) {
      throw new CliRequestException(
          "invalid-request", normalizedMessage(exception), invalidRequestHint(), exception);
    }
  }

  private JsonNode readRootNode(Path requestFile) {
    try {
      if ("-".equals(requestFile.toString())) {
        return Objects.requireNonNullElseGet(
            objectMapper.readTree(inputStream), NullNode::getInstance);
      }
      try (InputStream requestStream = Files.newInputStream(requestFile)) {
        return Objects.requireNonNullElseGet(
            objectMapper.readTree(requestStream), NullNode::getInstance);
      }
    } catch (IOException | JacksonException exception) {
      throw requestReadFailure(exception);
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
    int index = 0;
    for (JsonNode lineNode : linesNode) {
      ObjectNode lineObject = requireObjectNode(lineNode, "lines[%d]".formatted(index));
      rejectUnexpectedFields(lineObject, "lines[%d]".formatted(index), JOURNAL_LINE_FIELDS);
      lines.add(
          new JournalLine(
              new AccountCode(
                  requiredText(lineObject, MachineContract.JournalLineFields.ACCOUNT_CODE)),
              JournalLine.EntrySide.valueOf(
                  requiredText(lineObject, MachineContract.JournalLineFields.SIDE)),
              new Money(
                  new CurrencyCode(
                      requiredText(lineObject, MachineContract.JournalLineFields.CURRENCY_CODE)),
                  parseAmount(
                      requiredText(lineObject, MachineContract.JournalLineFields.AMOUNT)))));
      index++;
    }
    return lines;
  }

  private Optional<ReversalReference> readReversal(JsonNode reversalNode) {
    if (reversalNode == null || reversalNode.isNull()) {
      return Optional.empty();
    }
    ObjectNode reversalObject =
        requireObjectNode(reversalNode, MachineContract.PostEntryTopLevelFields.REVERSAL);
    rejectForbiddenField(reversalObject, MachineContract.ReversalFields.KIND);
    rejectUnexpectedFields(
        reversalObject, MachineContract.PostEntryTopLevelFields.REVERSAL, REVERSAL_FIELDS);
    return Optional.of(
        new ReversalReference(
            new dev.erst.fingrind.core.PostingId(
                requiredText(reversalObject, MachineContract.ReversalFields.PRIOR_POSTING_ID))));
  }

  private static void rejectForbiddenField(ObjectNode rootNode, String fieldName) {
    JsonNode fieldNode = rootNode.get(fieldName);
    if (fieldNode != null) {
      throw new IllegalArgumentException("Field is no longer accepted: " + fieldName);
    }
  }

  private static ObjectNode requiredObject(ObjectNode rootNode, String fieldName) {
    JsonNode fieldNode = rootNode.get(fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      throw new IllegalArgumentException("Missing required field: " + fieldName);
    }
    return requireObjectNode(fieldNode, fieldName);
  }

  private static JsonNode requiredArray(ObjectNode rootNode, String fieldName) {
    JsonNode fieldNode = rootNode.get(fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      throw new IllegalArgumentException("Missing required field: " + fieldName);
    }
    if (!fieldNode.isArray()) {
      throw new IllegalArgumentException("Field must be an array: " + fieldName);
    }
    return fieldNode;
  }

  private static String requiredText(ObjectNode rootNode, String fieldName) {
    JsonNode fieldNode = rootNode.get(fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      throw new IllegalArgumentException("Missing required field: " + fieldName);
    }
    if (!fieldNode.isString()) {
      throw new IllegalArgumentException("Field must be a string: " + fieldName);
    }
    return fieldNode.stringValue();
  }

  private static Optional<String> optionalText(ObjectNode rootNode, String fieldName) {
    JsonNode fieldNode = rootNode.get(fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      return Optional.empty();
    }
    if (!fieldNode.isString()) {
      throw new IllegalArgumentException("Field must be a string when present: " + fieldName);
    }
    return Optional.of(fieldNode.stringValue());
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

  private static ObjectMapper configuredObjectMapper() {
    return JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
  }

  private static CliRequestException requestReadFailure(Exception exception) {
    return new CliRequestException(
        "invalid-request",
        readFailureMessage(exception),
        "Run 'fingrind print-request-template' for a minimal valid request document.",
        exception);
  }

  private static String readFailureMessage(Exception exception) {
    Objects.requireNonNull(exception, "exception");
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      String duplicateField = duplicateFieldName(cause.getMessage());
      if (duplicateField != null) {
        return "Request JSON must not contain duplicate object keys. Duplicate key: "
            + duplicateField;
      }
    }
    return "Failed to read request JSON.";
  }

  private static String duplicateFieldName(String message) {
    Matcher matcher = DUPLICATE_FIELD_PATTERN.matcher(Objects.toString(message, ""));
    return matcher.find() ? matcher.group(1) : null;
  }

  private static ObjectNode requireRootObject(JsonNode rootNode) {
    if (rootNode.isNull()) {
      throw new IllegalArgumentException(ROOT_DOCUMENT_MUST_BE_OBJECT);
    }
    if (!rootNode.isObject()) {
      throw new IllegalArgumentException(ROOT_DOCUMENT_MUST_BE_OBJECT);
    }
    return (ObjectNode) rootNode;
  }

  private static ObjectNode requireObjectNode(JsonNode rootNode, String fieldName) {
    if (!rootNode.isObject()) {
      throw new IllegalArgumentException("Field must be an object: " + fieldName);
    }
    return (ObjectNode) rootNode;
  }

  private static void rejectUnexpectedFields(
      ObjectNode rootNode, String context, Set<String> acceptedFields) {
    rootNode
        .propertyStream()
        .map(java.util.Map.Entry::getKey)
        .filter(fieldName -> !acceptedFields.contains(fieldName))
        .findFirst()
        .ifPresent(
            fieldName -> {
              String qualifiedField =
                  context == null ? fieldName : context + "." + Objects.requireNonNull(fieldName);
              throw new IllegalArgumentException("Unexpected field: " + qualifiedField);
            });
  }
}
