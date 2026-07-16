package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.discovery.ScaffoldPlaceholders;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Guards scaffold placeholder values from entering committed CLI request commands. */
final class CliRequestPlaceholderValues {
  private CliRequestPlaceholderValues() {}

  /** Rejects every canonical scaffold value before a request reaches a command-specific parser. */
  static void rejectReservedScaffoldValues(JsonNode rootNode) {
    @Nullable String fieldPath = findReservedValuePath(rootNode, "");
    if (fieldPath != null) {
      throw new IllegalArgumentException(
          "Scaffold placeholder must be replaced before submission: "
              + publishedDirectRequestFieldPath(fieldPath));
    }
  }

  static String requiredRealProvenanceText(
      ObjectNode provenanceNode, String fieldName, String reservedValue) {
    return requiredRealText(provenanceNode, fieldName, reservedValue, "provenance.");
  }

  static String requiredRealText(
      ObjectNode objectNode,
      String fieldName,
      String reservedValue,
      @Nullable String contextPrefix) {
    String value = CliJsonFieldAccess.requiredText(objectNode, fieldName);
    if (reservedValue.equals(value)) {
      throw new IllegalArgumentException(
          "Scaffold placeholder must be replaced before submission: "
              + (contextPrefix == null ? "" : contextPrefix)
              + fieldName);
    }
    return value;
  }

  private static @Nullable String findReservedValuePath(JsonNode valueNode, String fieldPath) {
    if (valueNode.isTextual() && ScaffoldPlaceholders.isReserved(valueNode.stringValue())) {
      return fieldPath;
    }
    if (valueNode.isObject()) {
      for (Map.Entry<String, JsonNode> property :
          ((ObjectNode) valueNode).propertyStream().toList()) {
        @Nullable String reservedPath =
            findReservedValuePath(
                property.getValue(), appendObjectProperty(fieldPath, property.getKey()));
        if (reservedPath != null) {
          return reservedPath;
        }
      }
    }
    if (valueNode.isArray()) {
      int index = 0;
      for (JsonNode element : valueNode) {
        @Nullable String reservedPath =
            findReservedValuePath(element, "%s[%d]".formatted(fieldPath, index));
        if (reservedPath != null) {
          return reservedPath;
        }
        index++;
      }
    }
    return null;
  }

  private static String appendObjectProperty(String parentPath, String propertyName) {
    return parentPath.isEmpty() ? propertyName : parentPath + "." + propertyName;
  }

  private static String publishedDirectRequestFieldPath(String fieldPath) {
    return fieldPath.startsWith("evidence.")
        ? fieldPath.substring("evidence.".length())
        : fieldPath;
  }
}
