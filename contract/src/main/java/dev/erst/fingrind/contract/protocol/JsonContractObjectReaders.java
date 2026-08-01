package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Object-shaped node readers for protocol-owned JSON contract snapshots. */
final class JsonContractObjectReaders {
  private JsonContractObjectReaders() {}

  static @Nullable JsonNode nullableField(JsonNode document, String key) {
    Objects.requireNonNull(document, "document");
    Objects.requireNonNull(key, "key");
    return document.get(key);
  }

  static ObjectNode requireObject(JsonNode document, String key, String message) {
    @Nullable JsonNode value = nullableField(document, key);
    if (value == null) {
      throw new IllegalArgumentException(message);
    }
    return requireObjectNode(value, message);
  }

  static ObjectNode requireObjectNode(JsonNode value, String message) {
    if (!Objects.requireNonNull(value, "value").isObject()) {
      throw new IllegalArgumentException(message);
    }
    return (ObjectNode) value;
  }

  static void requireOnlyProperties(
      ObjectNode document, String objectLabel, Set<String> allowedProperties) {
    Objects.requireNonNull(document, "document");
    Objects.requireNonNull(objectLabel, "objectLabel");
    Set<String> normalizedAllowedProperties = Set.copyOf(allowedProperties);
    List<String> unexpectedProperties =
        document
            .propertyStream()
            .map(entry -> entry.getKey())
            .filter(property -> !normalizedAllowedProperties.contains(property))
            .sorted()
            .toList();
    if (!unexpectedProperties.isEmpty()) {
      throw new IllegalArgumentException(
          objectLabel
              + " must not declare unrecognized properties: "
              + String.join(", ", unexpectedProperties));
    }
  }
}
