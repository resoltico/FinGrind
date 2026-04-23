package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolLimits;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/** Shared JSON Schema helpers for the machine-readable request contract. */
final class MachineContractSchemaSupport {
  static final String JSON_SCHEMA_DIALECT = "https://json-schema.org/draft/2020-12/schema";
  private static final String NON_BLANK_TEXT_PATTERN = "^(?=\\S).*(?<=\\S)$";
  private static final String POSITIVE_DECIMAL_PATTERN = "^(?=.*[1-9])(?:0|[1-9]\\d*)(?:\\.\\d+)?$";

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

  static Map<String, Object> decimalAmountStringSchema(String description) {
    return orderedMap(
        "type", "string", "description", description, "pattern", POSITIVE_DECIMAL_PATTERN);
  }

  static Map<String, Object> pageLimitSchema() {
    return orderedMap(
        "type",
        "integer",
        "description",
        "Requested page size for list operations.",
        "minimum",
        ProtocolLimits.PAGE_LIMIT_MIN,
        "maximum",
        ProtocolLimits.PAGE_LIMIT_MAX);
  }

  static Map<String, Object> stripDialect(Map<String, Object> schema) {
    return orderedMapFromEntries(
        schema.entrySet().stream().filter(entry -> !"$schema".equals(entry.getKey())).toList());
  }

  static Map<String, Object> orderedMap(Object... keyValues) {
    return orderedMapFromEntries(
        IntStream.range(0, keyValues.length / 2)
            .mapToObj(index -> Map.entry((String) keyValues[index * 2], keyValues[index * 2 + 1]))
            .toList());
  }

  static Map<String, Object> orderedMapFromEntries(List<Map.Entry<String, Object>> entries) {
    return Collections.unmodifiableMap(
        entries.stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (left, right) -> right,
                    LinkedHashMap::new)));
  }

  static String operation(OperationId operationId) {
    return ProtocolCatalog.operationName(operationId);
  }
}
