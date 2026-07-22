package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.contract.protocol.ProtocolMoneyFields;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import java.util.List;
import java.util.Map;

/** Scalar JSON Schema builders for machine-readable contract fields. */
final class MachineContractScalarSchemas {
  private static final String NON_BLANK_TEXT_PATTERN = "^(?=\\S).*(?<=\\S)$";
  private static final String CURRENCY_CODE_PATTERN = "^[A-Z]{3}$";
  private static final String NON_NEGATIVE_INTEGER_STRING_PATTERN = "^(0|[1-9]\\d*)$";
  private static final String POSITIVE_INTEGER_STRING_PATTERN = "^[1-9]\\d*$";
  private static final String CANONICAL_UUID_PATTERN =
      "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

  private MachineContractScalarSchemas() {}

  static Map<String, Object> constSchema(String value, String description) {
    return MachineContractSchemaSupport.orderedMap("const", value, "description", description);
  }

  static Map<String, Object> enumStringSchema(String description, List<String> values) {
    return MachineContractSchemaSupport.orderedMap(
        "type", "string", "description", description, "enum", values);
  }

  static Map<String, Object> dateStringSchema(String description) {
    return MachineContractSchemaSupport.orderedMap(
        "type",
        "string",
        "description",
        description,
        "format",
        "date",
        "pattern",
        CanonicalTemporalText.LOCAL_DATE_PATTERN);
  }

  static Map<String, Object> instantStringSchema(String description) {
    return MachineContractSchemaSupport.orderedMap(
        "type",
        "string",
        "description",
        description,
        "format",
        "date-time",
        "pattern",
        CanonicalTemporalText.UTC_INSTANT_PATTERN);
  }

  static Map<String, Object> nonBlankStringSchema(String description) {
    return MachineContractSchemaSupport.orderedMap(
        "type", "string", "description", description, "pattern", NON_BLANK_TEXT_PATTERN);
  }

  static Map<String, Object> uuidStringSchema(String description) {
    return MachineContractSchemaSupport.orderedMap(
        "type",
        "string",
        "description",
        description,
        "format",
        "uuid",
        "pattern",
        CANONICAL_UUID_PATTERN);
  }

  static Map<String, Object> tokenStringSchema(String description, String pattern, int maxLength) {
    return MachineContractSchemaSupport.orderedMap(
        "type", "string", "description", description, "pattern", pattern, "maxLength", maxLength);
  }

  static Map<String, Object> currencyCodeSchema(String description) {
    return MachineContractSchemaSupport.orderedMap(
        "type",
        "string",
        "description",
        description,
        "pattern",
        CURRENCY_CODE_PATTERN,
        "minLength",
        3,
        "maxLength",
        3);
  }

  static Map<String, Object> minorUnitsStringSchema(String description, boolean positiveOnly) {
    return MachineContractSchemaSupport.orderedMap(
        "type",
        "string",
        "description",
        description,
        "pattern",
        positiveOnly ? POSITIVE_INTEGER_STRING_PATTERN : NON_NEGATIVE_INTEGER_STRING_PATTERN,
        "maxLength",
        Money.maxMinorUnitsDigitCount());
  }

  static Map<String, Object> moneyObjectSchema(String description, boolean positiveOnly) {
    return MachineContractSchemaSupport.objectSchema(
        description,
        MachineContractSchemaSupport.orderedMap(
            ProtocolMoneyFields.CURRENCY_CODE,
            currencyCodeSchema(
                "Three-letter ISO currency code from FinGrind's pinned currency registry."),
            ProtocolMoneyFields.MINOR_UNITS,
            minorUnitsStringSchema(
                positiveOnly
                    ? "Exact positive minor-unit amount encoded as one ASCII-digit string."
                    : "Exact non-negative minor-unit amount encoded as one ASCII-digit string.",
                positiveOnly)),
        ProtocolMoneyFields.fields());
  }

  static Map<String, Object> quantityTextSchema(String description) {
    return MachineContractSchemaSupport.orderedMap(
        "type",
        "string",
        "description",
        description,
        "pattern",
        "^(0|[1-9]\\d*)(?:\\.\\d{1," + Quantity.maxSupportedScale() + "})?$",
        "maxLength",
        Quantity.maxScaledUnitsDigitCount() + 1 + Quantity.maxSupportedScale());
  }

  static Map<String, Object> pageLimitSchema() {
    return MachineContractSchemaSupport.orderedMap(
        "type",
        "integer",
        "description",
        "Requested page size for list operations.",
        "minimum",
        ProtocolInteractionLimits.PAGE_LIMIT_MIN,
        "maximum",
        ProtocolInteractionLimits.PAGE_LIMIT_MAX);
  }

  static Map<String, Object> nonNegativeIntegerSchema(String description) {
    return MachineContractSchemaSupport.orderedMap(
        "type", "integer", "description", description, "minimum", 0);
  }
}
