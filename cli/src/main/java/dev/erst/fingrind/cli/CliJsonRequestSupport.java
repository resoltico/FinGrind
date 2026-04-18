package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolDeclareAccountFields;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Shared JSON request-reading helpers for the CLI transport. */
final class CliJsonRequestSupport {
  static final Pattern DUPLICATE_FIELD_PATTERN =
      Pattern.compile("(?i)Duplicate[^'\"]*['\"]([^'\"]+)['\"]");
  static final String ROOT_DOCUMENT_MUST_BE_OBJECT = "Request JSON document must be an object.";
  static final Set<String> DECLARE_ACCOUNT_FIELDS =
      Set.of(
          ProtocolDeclareAccountFields.ACCOUNT_CODE,
          ProtocolDeclareAccountFields.ACCOUNT_NAME,
          ProtocolDeclareAccountFields.NORMAL_BALANCE);
  static final Set<String> POST_ENTRY_TOP_LEVEL_FIELDS =
      Set.copyOf(ProtocolPostEntryFields.topLevelFields());
  static final Set<String> PROVENANCE_FIELDS =
      Set.copyOf(ProtocolPostEntryFields.provenanceFields());
  static final Set<String> JOURNAL_LINE_FIELDS =
      Set.copyOf(ProtocolPostEntryFields.journalLineFields());
  static final Set<String> REVERSAL_FIELDS = Set.copyOf(ProtocolPostEntryFields.reversalFields());
  static final Set<String> LEDGER_PLAN_FIELDS = Set.copyOf(ProtocolLedgerPlanFields.planFields());
  static final Set<String> LEDGER_STEP_FIELDS = Set.copyOf(ProtocolLedgerPlanFields.stepFields());
  static final Set<String> LEDGER_QUERY_FIELDS = Set.copyOf(ProtocolLedgerPlanFields.queryFields());
  static final Set<String> LEDGER_ASSERTION_FIELDS =
      Set.copyOf(ProtocolLedgerPlanFields.assertionFields());

  private CliJsonRequestSupport() {}

  static ObjectMapper configuredObjectMapper() {
    return JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
  }

  static String normalizedMessage(RuntimeException exception) {
    return Objects.requireNonNullElse(exception.getMessage(), "Request is invalid.");
  }

  static String invalidRequestHint() {
    return "Run 'fingrind "
        + ProtocolCatalog.operationName(OperationId.PRINT_REQUEST_TEMPLATE)
        + "' for a minimal valid request document, or 'fingrind "
        + ProtocolCatalog.operationName(OperationId.CAPABILITIES)
        + "' for accepted enums and fields.";
  }

  static CliRequestException requestReadFailure(Exception exception) {
    return new CliRequestException(
        "invalid-request",
        readFailureMessage(exception),
        "Run 'fingrind "
            + ProtocolCatalog.operationName(OperationId.PRINT_REQUEST_TEMPLATE)
            + "' for a minimal valid request document.",
        exception);
  }

  static String readFailureMessage(Exception exception) {
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

  static @Nullable String duplicateFieldName(@Nullable String message) {
    Matcher matcher = DUPLICATE_FIELD_PATTERN.matcher(Objects.toString(message, ""));
    return matcher.find() ? matcher.group(1) : null;
  }

  static ObjectNode requireRootObject(JsonNode rootNode) {
    if (!rootNode.isObject()) {
      throw new IllegalArgumentException(ROOT_DOCUMENT_MUST_BE_OBJECT);
    }
    return (ObjectNode) rootNode;
  }

  static ObjectNode requireObjectNode(JsonNode rootNode, String fieldName) {
    if (!rootNode.isObject()) {
      throw new IllegalArgumentException("Field must be an object: " + fieldName);
    }
    return (ObjectNode) rootNode;
  }

  static void rejectUnexpectedFields(
      ObjectNode rootNode, @Nullable String context, Set<String> acceptedFields) {
    List<String> unexpectedFields =
        rootNode
            .propertyStream()
            .map(java.util.Map.Entry::getKey)
            .filter(fieldName -> !acceptedFields.contains(fieldName))
            .map(fieldName -> context == null ? fieldName : context + "." + fieldName)
            .toList();
    if (unexpectedFields.isEmpty()) {
      return;
    }
    if (unexpectedFields.size() == 1) {
      throw new IllegalArgumentException("Unexpected field: " + unexpectedFields.getFirst());
    }
    throw new IllegalArgumentException("Unexpected fields: " + String.join(", ", unexpectedFields));
  }

  static void rejectForbiddenField(ObjectNode rootNode, String fieldName) {
    if (rootNode.get(fieldName) != null) {
      throw new IllegalArgumentException("Field is no longer accepted: " + fieldName);
    }
  }

  static ObjectNode requiredObject(ObjectNode rootNode, String fieldName) {
    JsonNode fieldNode = rootNode.get(fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      throw new IllegalArgumentException("Missing required field: " + fieldName);
    }
    return requireObjectNode(fieldNode, fieldName);
  }

  static JsonNode requiredArray(ObjectNode rootNode, String fieldName) {
    JsonNode fieldNode = rootNode.get(fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      throw new IllegalArgumentException("Missing required field: " + fieldName);
    }
    if (!fieldNode.isArray()) {
      throw new IllegalArgumentException("Field must be an array: " + fieldName);
    }
    return fieldNode;
  }

  static String requiredText(ObjectNode rootNode, String fieldName) {
    JsonNode fieldNode = rootNode.get(fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      throw new IllegalArgumentException("Missing required field: " + fieldName);
    }
    if (!fieldNode.isString()) {
      throw new IllegalArgumentException("Field must be a string: " + fieldName);
    }
    return fieldNode.stringValue();
  }

  static Optional<String> optionalText(ObjectNode rootNode, String fieldName) {
    JsonNode fieldNode = rootNode.get(fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      return Optional.empty();
    }
    if (!fieldNode.isString()) {
      throw new IllegalArgumentException("Field must be a string when present: " + fieldName);
    }
    return Optional.of(fieldNode.stringValue());
  }

  static OptionalInt optionalInt(ObjectNode rootNode, String fieldName) {
    JsonNode fieldNode = rootNode.get(fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      return OptionalInt.empty();
    }
    if (!fieldNode.isInt()) {
      throw new IllegalArgumentException("Field must be an integer when present: " + fieldName);
    }
    return OptionalInt.of(fieldNode.intValue());
  }

  static @Nullable ObjectNode optionalObject(ObjectNode rootNode, String fieldName) {
    JsonNode fieldNode = rootNode.get(fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      return null;
    }
    return requireObjectNode(fieldNode, fieldName);
  }

  static BigDecimal parseAmount(String amountText) {
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

  static <T> T parseWireValue(
      String rawValue, String fieldName, List<String> acceptedValues, Function<String, T> parser) {
    try {
      return parser.apply(rawValue);
    } catch (IllegalArgumentException exception) {
      throw unsupportedValue(fieldName, rawValue, acceptedValues, exception);
    }
  }

  static IllegalArgumentException unsupportedValue(
      String fieldName, String rawValue, List<String> acceptedValues, @Nullable Throwable cause) {
    return new IllegalArgumentException(
        "Unsupported value for "
            + fieldName
            + ": "
            + rawValue
            + ". Accepted values: "
            + String.join(", ", acceptedValues)
            + ".",
        cause);
  }
}
