package dev.erst.fingrind.cli;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/** Guards scaffold placeholder values from entering committed CLI request commands. */
final class CliRequestPlaceholderValues {
  private CliRequestPlaceholderValues() {}

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
}
