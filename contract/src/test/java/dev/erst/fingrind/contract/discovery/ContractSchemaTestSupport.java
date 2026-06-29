package dev.erst.fingrind.contract.discovery;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shared helpers for asserting discovery schema fragments in contract tests. */
final class ContractSchemaTestSupport {
  private ContractSchemaTestSupport() {}

  @SuppressWarnings("unchecked")
  static Map<String, Object> objectMap(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  static List<String> stringList(Object value) {
    return ((List<Object>) value).stream().map(String.class::cast).toList();
  }

  static Map<String, Object> schemaProperty(Map<String, Object> schema, String propertyName) {
    return objectMap(requiredValue(objectMap(requiredValue(schema, "properties")), propertyName));
  }

  static Map<String, Object> sourceDocumentTypeSchema(
      ContractRequestShapes.BookkeepingEntryRequestShapeDescriptor bookkeepingEntryShape) {
    Map<String, Object> evidenceSchema = schemaProperty(bookkeepingEntryShape.schema(), "evidence");
    Map<String, Object> sourceDocumentsSchema = schemaProperty(evidenceSchema, "sourceDocuments");
    Map<String, Object> sourceDocumentItemSchema =
        objectMap(requiredValue(sourceDocumentsSchema, "items"));
    return objectMap(
        requiredValue(
            objectMap(requiredValue(sourceDocumentItemSchema, "properties")),
            "sourceDocumentType"));
  }

  static Object requiredValue(Map<String, Object> values, String key) {
    return Objects.requireNonNull(values.get(key), () -> "Missing schema key " + key);
  }
}
