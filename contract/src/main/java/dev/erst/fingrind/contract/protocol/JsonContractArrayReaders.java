package dev.erst.fingrind.contract.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Array-shaped node readers for protocol-owned JSON contract snapshots. */
final class JsonContractArrayReaders {
  private JsonContractArrayReaders() {}

  static List<String> optionalStringArray(JsonNode document, String key) {
    @Nullable JsonNode value = field(document, key);
    return value == null || value.isNull() ? List.of() : requireStringArrayValue(value, key);
  }

  static List<String> requireStringArray(JsonNode document, String key) {
    @Nullable JsonNode value = field(document, key);
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

  private static @Nullable JsonNode field(JsonNode document, String key) {
    return Objects.requireNonNull(document, "document").get(Objects.requireNonNull(key, "key"));
  }
}
