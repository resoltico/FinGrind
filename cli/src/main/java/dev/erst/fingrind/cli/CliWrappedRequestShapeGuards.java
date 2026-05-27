package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonStructureAccess.nullableField;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requireObjectNode;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Rejects wrapped request payload shapes when the public contract requires top-level fields. */
final class CliWrappedRequestShapeGuards {
  private CliWrappedRequestShapeGuards() {}

  static void rejectWrappedTopLevelPayload(
      ObjectNode rootNode,
      String wrapperFieldName,
      Set<String> nestedAcceptedFields,
      String message) {
    @Nullable JsonNode wrappedNode = nullableField(rootNode, wrapperFieldName);
    if (wrappedNode == null || wrappedNode.isNull() || !wrappedNode.isObject()) {
      return;
    }
    ObjectNode wrappedObject = requireObjectNode(wrappedNode, wrapperFieldName);
    List<String> wrappedFields =
        CliJsonStructureAccess.unexpectedFields(wrappedObject, null, nestedAcceptedFields);
    if (!wrappedFields.isEmpty()) {
      return;
    }
    List<String> topLevelAcceptedFields =
        rootNode
            .propertyStream()
            .map(Map.Entry::getKey)
            .filter(nestedAcceptedFields::contains)
            .toList();
    if (!topLevelAcceptedFields.isEmpty()) {
      return;
    }
    throw new IllegalArgumentException(message);
  }
}
