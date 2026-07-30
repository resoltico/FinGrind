package dev.erst.fingrind.contract.protocol;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
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
    return JsonContractObjectReaders.nullableField(document, key);
  }

  static ObjectNode requireObject(JsonNode document, String key, String message) {
    return JsonContractObjectReaders.requireObject(document, key, message);
  }

  static ObjectNode requireObjectNode(JsonNode value, String message) {
    return JsonContractObjectReaders.requireObjectNode(value, message);
  }

  static String requireText(JsonNode document, String key) {
    return JsonContractScalarReaders.requireText(document, key);
  }

  static String requireExactText(JsonNode document, String key) {
    return JsonContractScalarReaders.requireExactText(document, key);
  }

  static boolean requireBoolean(JsonNode document, String key) {
    return JsonContractScalarReaders.requireBoolean(document, key);
  }

  static int requireInt(JsonNode document, String key) {
    return JsonContractScalarReaders.requireInt(document, key);
  }

  static void requireOnlyProperties(
      ObjectNode document, String objectLabel, Set<String> allowedProperties) {
    JsonContractObjectReaders.requireOnlyProperties(document, objectLabel, allowedProperties);
  }

  static List<String> optionalStringArray(JsonNode document, String key) {
    return JsonContractArrayReaders.optionalStringArray(document, key);
  }

  static List<String> requireStringArray(JsonNode document, String key) {
    return JsonContractArrayReaders.requireStringArray(document, key);
  }
}
