package dev.erst.fingrind.contract.protocol;

import java.io.InputStream;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Facade over resource loading and node-reading for protocol-owned JSON contract snapshots. */
final class JsonContractResourceSupport {
  private JsonContractResourceSupport() {}

  static ObjectNode loadObject(
      @Nullable InputStream resourceStream, String resourcePath, String contractLabel) {
    return JsonContractResourceLoader.loadObject(resourceStream, resourcePath, contractLabel);
  }

  static @Nullable JsonNode nullableField(JsonNode document, String key) {
    return JsonContractNodeReaders.nullableField(document, key);
  }

  static ObjectNode requireObject(JsonNode document, String key, String message) {
    return JsonContractNodeReaders.requireObject(document, key, message);
  }

  static ObjectNode requireObjectNode(JsonNode value, String message) {
    return JsonContractNodeReaders.requireObjectNode(value, message);
  }

  static String requireText(JsonNode document, String key) {
    return JsonContractNodeReaders.requireText(document, key);
  }

  static boolean requireBoolean(JsonNode document, String key) {
    return JsonContractNodeReaders.requireBoolean(document, key);
  }

  static int requireInt(JsonNode document, String key) {
    return JsonContractNodeReaders.requireInt(document, key);
  }

  static List<String> optionalStringArray(JsonNode document, String key) {
    return JsonContractNodeReaders.optionalStringArray(document, key);
  }

  static List<String> requireStringArray(JsonNode document, String key) {
    return JsonContractNodeReaders.requireStringArray(document, key);
  }
}
