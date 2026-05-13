package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolMoneyFields;
import dev.erst.fingrind.core.InteractionLimits;
import dev.erst.fingrind.core.Money;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/** Shared JSON Schema helpers for the machine-readable request contract. */
final class MachineContractSchemaSupport {
  static final String JSON_SCHEMA_DIALECT = "https://json-schema.org/draft/2020-12/schema";
  private static final String NON_BLANK_TEXT_PATTERN = "^(?=\\S).*(?<=\\S)$";
  private static final String CURRENCY_CODE_PATTERN = "^[A-Z]{3}$";
  private static final String NON_NEGATIVE_INTEGER_STRING_PATTERN = "^(0|[1-9]\\d*)$";
  private static final String POSITIVE_INTEGER_STRING_PATTERN = "^[1-9]\\d*$";

  private MachineContractSchemaSupport() {}

  static Map<String, Object> rootObjectSchema(
      String description, Map<String, Object> properties, List<String> required) {
    return orderedMapFromEntries(
        Stream.concat(
                Stream.<Map.Entry<String, Object>>of(Map.entry("$schema", JSON_SCHEMA_DIALECT)),
                objectSchema(description, properties, required).entrySet().stream())
            .toList());
  }

  static Map<String, Object> objectSchema(
      String description, Map<String, Object> properties, List<String> required) {
    return orderedMap(
        "type",
        "object",
        "description",
        description,
        "additionalProperties",
        false,
        "required",
        required,
        "properties",
        properties);
  }

  static Map<String, Object> rootObjectSchema(
      String description, List<MachineContractFieldSpec> fields) {
    return rootObjectSchema(description, properties(fields), requiredFieldNames(fields));
  }

  static Map<String, Object> objectSchema(
      String description, List<MachineContractFieldSpec> fields) {
    return objectSchema(description, properties(fields), requiredFieldNames(fields));
  }

  static Map<String, Object> arraySchema(
      String description, Map<String, Object> items, int minItems, @Nullable Integer maxItems) {
    Stream<Map.Entry<String, Object>> entries =
        Stream.of(
            Map.entry("type", "array"),
            Map.entry("description", description),
            Map.entry("items", items),
            Map.entry("minItems", minItems));
    if (maxItems != null) {
      entries = Stream.concat(entries, Stream.of(Map.entry("maxItems", maxItems)));
    }
    return orderedMapFromEntries(entries.toList());
  }

  static Map<String, Object> arraySchema(
      String description, Map<String, Object> items, int minItems) {
    return orderedMap(
        "type", "array", "description", description, "items", items, "minItems", minItems);
  }

  static Map<String, Object> oneOfSchema(String description, List<Map<String, Object>> variants) {
    return orderedMap("description", description, "oneOf", variants);
  }

  static Map<String, Object> constSchema(String value, String description) {
    return orderedMap("const", value, "description", description);
  }

  static Map<String, Object> enumStringSchema(String description, List<String> values) {
    return orderedMap("type", "string", "description", description, "enum", values);
  }

  static Map<String, Object> dateStringSchema(String description) {
    return orderedMap("type", "string", "description", description, "format", "date");
  }

  static Map<String, Object> nonBlankStringSchema(String description) {
    return orderedMap(
        "type", "string", "description", description, "pattern", NON_BLANK_TEXT_PATTERN);
  }

  static Map<String, Object> tokenStringSchema(String description, String pattern, int maxLength) {
    return orderedMap(
        "type", "string", "description", description, "pattern", pattern, "maxLength", maxLength);
  }

  static Map<String, Object> currencyCodeSchema(String description) {
    return orderedMap(
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
    return orderedMap(
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
    return objectSchema(
        description,
        orderedMap(
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

  static Map<String, Object> pageLimitSchema() {
    return orderedMap(
        "type",
        "integer",
        "description",
        "Requested page size for list operations.",
        "minimum",
        InteractionLimits.PAGE_LIMIT_MIN,
        "maximum",
        InteractionLimits.PAGE_LIMIT_MAX);
  }

  static Map<String, Object> stripDialect(Map<String, Object> schema) {
    return orderedMapFromEntries(
        schema.entrySet().stream().filter(entry -> !"$schema".equals(entry.getKey())).toList());
  }

  static Map<String, Object> orderedMap(Object... keyValues) {
    if (keyValues.length % 2 != 0) {
      throw new IllegalArgumentException(
          "orderedMap requires an even number of key/value arguments.");
    }
    return orderedMapFromEntries(
        IntStream.range(0, keyValues.length / 2)
            .mapToObj(
                index -> {
                  Object key = keyValues[index * 2];
                  if (!(key instanceof String stringKey)) {
                    throw new IllegalArgumentException(
                        "orderedMap keys must be non-null Strings at argument index "
                            + (index * 2)
                            + ".");
                  }
                  Object value = keyValues[index * 2 + 1];
                  if (value == null) {
                    throw new IllegalArgumentException(
                        "orderedMap values must be non-null at argument index "
                            + (index * 2 + 1)
                            + ".");
                  }
                  return Map.entry(stringKey, value);
                })
            .toList());
  }

  static Map<String, Object> orderedMapFromEntries(List<Map.Entry<String, Object>> entries) {
    var ordered = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, Object> entry : entries) {
      String key = entry.getKey();
      if (ordered.containsKey(key)) {
        throw new IllegalArgumentException("Duplicate schema key: " + key);
      }
      ordered.put(key, entry.getValue());
    }
    return Collections.unmodifiableMap(ordered);
  }

  static String operation(OperationId operationId) {
    return ProtocolCatalog.operationName(operationId);
  }

  static List<ContractRequestShapes.RequestFieldDescriptor> requestFieldDescriptors(
      List<MachineContractFieldSpec> fields) {
    return fields.stream().map(MachineContractFieldSpec::descriptor).toList();
  }

  private static Map<String, Object> properties(List<MachineContractFieldSpec> fields) {
    return orderedMapFromEntries(
        fields.stream()
            .filter(MachineContractFieldSpec::acceptsInput)
            .map(field -> Map.entry(field.name(), (Object) field.inputSchema()))
            .toList());
  }

  private static List<String> requiredFieldNames(List<MachineContractFieldSpec> fields) {
    return fields.stream()
        .filter(MachineContractFieldSpec::requiredInSchema)
        .map(MachineContractFieldSpec::name)
        .toList();
  }
}
