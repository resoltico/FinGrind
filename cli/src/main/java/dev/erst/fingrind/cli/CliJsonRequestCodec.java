package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.ContractErrors;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolDeclareAccountFields;
import dev.erst.fingrind.contract.protocol.ProtocolLedgerPlanFields;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import java.math.BigDecimal;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Shared JSON request-reading helpers for the CLI transport. */
final class CliJsonRequestCodec {
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
  private static final ObjectMapper LENIENT_OBJECT_MAPPER = JsonMapper.builder().build();
  private static final ObjectMapper STRICT_DUPLICATE_OBJECT_MAPPER =
      JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

  private CliJsonRequestCodec() {}

  static ObjectMapper configuredObjectMapper() {
    return JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
  }

  static String normalizedMessage(RuntimeException exception) {
    return Objects.requireNonNullElse(exception.getMessage(), "Request is invalid.");
  }

  static String postEntryRequestHint() {
    return "Run '"
        + CliInvocationText.commandExample(OperationId.PRINT_REQUEST_TEMPLATE)
        + "' for the canonical request scaffold, then replace its scaffold placeholders before submission, or run '"
        + CliInvocationText.commandExample(OperationId.CAPABILITIES)
        + "' for accepted enums and fields.";
  }

  static String declareAccountRequestHint() {
    return "Run '"
        + CliInvocationText.commandExample(OperationId.CAPABILITIES)
        + "' for the accepted account-declaration request fields and enums.";
  }

  static String ledgerPlanRequestHint() {
    return "Run '"
        + CliInvocationText.commandExample(OperationId.PRINT_PLAN_TEMPLATE)
        + "' for the canonical ledger plan scaffold, then replace its scaffold placeholders before submission, or run '"
        + CliInvocationText.commandExample(OperationId.CAPABILITIES)
        + "' for accepted enums and fields.";
  }

  static CliRequestException requestReadFailure(Exception exception, String hint) {
    CliReadErrorDetails readErrorDetails = readErrorDetails(exception);
    return new CliRequestException(
        ContractErrors.Descriptor.INVALID_REQUEST.code(),
        readErrorDetails.message(),
        hint,
        exception,
        readErrorDetails.details());
  }

  static CliRequestException requestReadFailure(
      Path requestFile, Exception exception, String invalidJsonHint) {
    Objects.requireNonNull(requestFile, "requestFile");
    Objects.requireNonNull(exception, "exception");
    if (exception instanceof JacksonException) {
      return requestReadFailure(exception, invalidJsonHint);
    }
    return new CliRequestException(
        ContractErrors.Descriptor.INVALID_REQUEST.code(),
        requestTransportFailureMessage(requestFile, exception),
        requestTransportFailureHint(requestFile),
        exception);
  }

  static CliRequestException duplicateObjectKeyFailure(String hint) {
    return new CliRequestException(
        ContractErrors.Descriptor.INVALID_REQUEST.code(),
        "Request JSON must not contain duplicate object keys.",
        hint,
        null);
  }

  static String readFailureMessage(Exception exception) {
    return readErrorDetails(exception).message();
  }

  private static CliReadErrorDetails readErrorDetails(Exception exception) {
    Objects.requireNonNull(exception, "exception");
    if (exception instanceof JacksonException jacksonException) {
      JsonParseLocation parseLocation = parseLocation(jacksonException);
      if (parseLocation != null) {
        return new CliReadErrorDetails(
            "Failed to read request JSON at line "
                + parseLocation.line()
                + ", column "
                + parseLocation.column()
                + ".",
            new dev.erst.fingrind.cli.json.CliErrorJsonModels.InvalidJsonDetails(
                Objects.requireNonNullElse(
                    jacksonException.getOriginalMessage(),
                    Objects.requireNonNullElse(
                        jacksonException.getMessage(), "Request JSON is invalid.")),
                parseLocation.line(),
                parseLocation.column()));
      }
    }
    return new CliReadErrorDetails("Failed to read request JSON.", null);
  }

  static @Nullable JsonParseLocation parseLocation(JacksonException exception) {
    Objects.requireNonNull(exception, "exception");
    TokenStreamLocation location = exception.getLocation();
    if (location == null) {
      return null;
    }
    int lineNumber = location.getLineNr();
    if (lineNumber <= 0) {
      return null;
    }
    int columnNumber = location.getColumnNr();
    if (columnNumber <= 0) {
      return null;
    }
    return new JsonParseLocation(lineNumber, columnNumber);
  }

  private static String requestTransportFailureMessage(Path requestFile, Exception exception) {
    if (ProtocolOptions.STDIN_TOKEN.equals(requestFile.toString())) {
      return "Failed to read request JSON from standard input.";
    }
    String normalizedPath = requestFile.toAbsolutePath().normalize().toString();
    if (exception instanceof NoSuchFileException) {
      return "Request file does not exist: " + normalizedPath + ".";
    }
    if (exception instanceof AccessDeniedException) {
      return "Request file is not readable: " + normalizedPath + ".";
    }
    return "Failed to read request file: " + normalizedPath + ".";
  }

  private static String requestTransportFailureHint(Path requestFile) {
    if (ProtocolOptions.STDIN_TOKEN.equals(requestFile.toString())) {
      return "Provide one readable JSON document on standard input, or pass --request-file <path> to read it from a file.";
    }
    return "Verify that the selected --request-file exists and is readable, or pass --request-file - to read one JSON document from standard input.";
  }

  private record CliReadErrorDetails(
      String message,
      dev.erst.fingrind.cli.json.CliErrorJsonModels.@Nullable ErrorDetails details) {
    private CliReadErrorDetails {
      Objects.requireNonNull(message, "message");
    }
  }

  record JsonParseLocation(int line, int column) {
    JsonParseLocation {
      if (line <= 0) {
        throw new IllegalArgumentException("line must be positive");
      }
      if (column <= 0) {
        throw new IllegalArgumentException("column must be positive");
      }
    }
  }

  static boolean hasDuplicateObjectKeys(byte[] requestBytes) throws java.io.IOException {
    Objects.requireNonNull(requestBytes, "requestBytes");
    try {
      STRICT_DUPLICATE_OBJECT_MAPPER.readTree(requestBytes);
      return false;
    } catch (JacksonException strictFailure) {
      try {
        LENIENT_OBJECT_MAPPER.readTree(requestBytes);
        return true;
      } catch (JacksonException syntaxFailure) {
        return false;
      }
    }
  }

  static ObjectNode requireRootObject(JsonNode rootNode) {
    if (!rootNode.isObject()) {
      throw new IllegalArgumentException(ROOT_DOCUMENT_MUST_BE_OBJECT);
    }
    return (ObjectNode) rootNode;
  }

  static ObjectNode requireObjectNode(JsonNode valueNode, String fieldName) {
    if (!valueNode.isObject()) {
      throw new IllegalArgumentException("Field must be an object: " + fieldName);
    }
    return (ObjectNode) valueNode;
  }

  static void rejectUnexpectedFields(
      ObjectNode rootNode, @Nullable String context, Set<String> acceptedFields) {
    List<String> unexpectedFields = unexpectedFields(rootNode, context, acceptedFields);
    if (unexpectedFields.isEmpty()) {
      return;
    }
    throw unexpectedFieldsFailure(unexpectedFields);
  }

  static List<String> unexpectedFields(
      ObjectNode rootNode, @Nullable String context, Set<String> acceptedFields) {
    return rootNode
        .propertyStream()
        .map(java.util.Map.Entry::getKey)
        .filter(fieldName -> !acceptedFields.contains(fieldName))
        .map(fieldName -> context == null ? fieldName : context + "." + fieldName)
        .toList();
  }

  static IllegalArgumentException unexpectedFieldsFailure(List<String> unexpectedFields) {
    if (unexpectedFields.size() == 1) {
      return new IllegalArgumentException("Unexpected field: " + unexpectedFields.getFirst());
    }
    return new IllegalArgumentException(
        "Unexpected fields: " + String.join(", ", unexpectedFields));
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

  static Optional<ObjectNode> optionalObject(ObjectNode rootNode, String fieldName) {
    JsonNode fieldNode = rootNode.get(fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      return Optional.empty();
    }
    return Optional.of(requireObjectNode(fieldNode, fieldName));
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
