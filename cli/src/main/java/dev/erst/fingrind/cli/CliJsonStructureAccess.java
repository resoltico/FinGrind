package dev.erst.fingrind.cli;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Owns structural object/array validation for CLI JSON request documents. */
final class CliJsonStructureAccess {
  private static final String ROOT_DOCUMENT_MUST_BE_OBJECT =
      "Request JSON document must be an object.";

  private CliJsonStructureAccess() {}

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
    if (nullableField(rootNode, fieldName) != null) {
      throw new IllegalArgumentException("Field is no longer accepted: " + fieldName);
    }
  }

  static ObjectNode requiredObject(ObjectNode rootNode, String fieldName) {
    @Nullable JsonNode fieldNode = nullableField(rootNode, fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      throw new IllegalArgumentException("Missing required field: " + fieldName);
    }
    return requireObjectNode(fieldNode, fieldName);
  }

  static JsonNode requiredArray(ObjectNode rootNode, String fieldName) {
    @Nullable JsonNode fieldNode = nullableField(rootNode, fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      throw new IllegalArgumentException("Missing required field: " + fieldName);
    }
    if (!fieldNode.isArray()) {
      throw new IllegalArgumentException("Field must be an array: " + fieldName);
    }
    return fieldNode;
  }

  static Optional<ObjectNode> optionalObject(ObjectNode rootNode, String fieldName) {
    @Nullable JsonNode fieldNode = nullableField(rootNode, fieldName);
    if (fieldNode == null || fieldNode.isNull()) {
      return Optional.empty();
    }
    return Optional.of(requireObjectNode(fieldNode, fieldName));
  }

  static @Nullable JsonNode nullableField(ObjectNode rootNode, String fieldName) {
    return rootNode.get(fieldName);
  }
}
