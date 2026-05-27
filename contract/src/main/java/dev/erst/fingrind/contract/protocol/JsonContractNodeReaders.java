package dev.erst.fingrind.contract.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Node-reading helpers for protocol-owned JSON contract snapshots. */
final class JsonContractNodeReaders {
  private JsonContractNodeReaders() {}

  static @Nullable JsonNode nullableField(JsonNode document, String key) {
    Objects.requireNonNull(document, "document");
    Objects.requireNonNull(key, "key");
    return document.get(key);
  }

  static ObjectNode requireObject(JsonNode document, String key, String message) {
    @Nullable JsonNode value = nullableField(document, key);
    if (value == null || !value.isObject()) {
      throw new IllegalArgumentException(message);
    }
    return (ObjectNode) value;
  }

  static ObjectNode requireObjectNode(JsonNode value, String message) {
    if (!value.isObject()) {
      throw new IllegalArgumentException(message);
    }
    return (ObjectNode) value;
  }

  static String requireText(JsonNode document, String key) {
    @Nullable JsonNode value = nullableField(document, key);
    String normalized =
        value == null || value.isNull() || !value.isString() ? "" : value.stringValue().strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(key + " must be a non-blank JSON string.");
    }
    return normalized;
  }

  static boolean requireBoolean(JsonNode document, String key) {
    @Nullable JsonNode value = nullableField(document, key);
    if (value == null || value.isNull() || !value.isBoolean()) {
      throw new IllegalArgumentException(key + " must be one JSON boolean.");
    }
    return value.booleanValue();
  }

  static int requireInt(JsonNode document, String key) {
    @Nullable JsonNode value = nullableField(document, key);
    OptionalInt intValue =
        value == null || value.isNull() ? OptionalInt.empty() : value.intValueOpt();
    if (intValue.isEmpty()) {
      throw new IllegalArgumentException(key + " must be one JSON integer.");
    }
    return intValue.orElseThrow();
  }

  static List<String> optionalStringArray(JsonNode document, String key) {
    @Nullable JsonNode value = nullableField(document, key);
    if (value == null || value.isNull()) {
      return List.of();
    }
    return requireStringArrayValue(value, key);
  }

  static List<String> requireStringArray(JsonNode document, String key) {
    @Nullable JsonNode value = nullableField(document, key);
    if (value == null || value.isNull()) {
      throw new IllegalArgumentException(key + " must be a JSON array of strings.");
    }
    return requireStringArrayValue(value, key);
  }

  private static List<String> requireStringArrayValue(JsonNode value, String key) {
    if (!value.isArray()) {
      throw new IllegalArgumentException(key + " must be a JSON array of strings.");
    }
    List<String> values = new ArrayList<>();
    for (JsonNode element : value) {
      String normalized = element.isString() ? element.stringValue().strip() : "";
      if (normalized.isEmpty()) {
        throw new IllegalArgumentException(key + " must be a JSON array of strings.");
      }
      values.add(normalized);
    }
    return List.copyOf(values);
  }
}
