package dev.erst.fingrind.cli;

import java.util.Optional;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Shared scalar field-access helpers for CLI JSON requests. */
final class CliJsonFieldAccess {
  private CliJsonFieldAccess() {}

  static String requiredText(ObjectNode rootNode, String fieldName) {
    @Nullable JsonNode fieldNode = CliJsonStructureAccess.nullableField(rootNode, fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      throw new IllegalArgumentException("Missing required field: " + fieldName);
    }
    if (!fieldNode.isString()) {
      throw new IllegalArgumentException("Field must be a string: " + fieldName);
    }
    return fieldNode.stringValue();
  }

  static Optional<String> optionalText(ObjectNode rootNode, String fieldName) {
    @Nullable JsonNode fieldNode = CliJsonStructureAccess.nullableField(rootNode, fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      return Optional.empty();
    }
    if (!fieldNode.isString()) {
      throw new IllegalArgumentException("Field must be a string when present: " + fieldName);
    }
    return Optional.of(fieldNode.stringValue());
  }

  static OptionalInt optionalInt(ObjectNode rootNode, String fieldName) {
    @Nullable JsonNode fieldNode = CliJsonStructureAccess.nullableField(rootNode, fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      return OptionalInt.empty();
    }
    OptionalInt intValue =
        !fieldNode.isIntegralNumber() ? OptionalInt.empty() : fieldNode.intValueOpt();
    if (intValue.isEmpty()) {
      throw new IllegalArgumentException("Field must be an integer when present: " + fieldName);
    }
    return intValue;
  }

  static int requiredInt(ObjectNode rootNode, String fieldName) {
    OptionalInt value = optionalInt(rootNode, fieldName);
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Missing required field: " + fieldName);
    }
    return value.orElseThrow();
  }
}
