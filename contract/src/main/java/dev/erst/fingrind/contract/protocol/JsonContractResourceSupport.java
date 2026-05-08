package dev.erst.fingrind.contract.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

/** Shared JSON resource loading helpers for protocol-owned contract snapshots. */
final class JsonContractResourceSupport {
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  private JsonContractResourceSupport() {}

  static JsonNode loadObject(
      @Nullable InputStream resourceStream, String resourcePath, String contractLabel) {
    Objects.requireNonNull(resourcePath, "resourcePath");
    Objects.requireNonNull(contractLabel, "contractLabel");
    if (resourceStream == null) {
      throw new IllegalStateException("Missing " + contractLabel + " resource: " + resourcePath);
    }
    byte[] resourceBytes;
    try (resourceStream) {
      resourceBytes = resourceStream.readAllBytes();
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Failed to load " + contractLabel + " resource: " + resourcePath, exception);
    }
    JsonNode document = parseDocument(resourceBytes, resourcePath, contractLabel);
    if (!document.isObject()) {
      throw new IllegalArgumentException(
          contractLabel + " resource must contain one top-level JSON object: " + resourcePath);
    }
    return document;
  }

  private static JsonNode parseDocument(
      byte[] resourceBytes, String resourcePath, String contractLabel) {
    if (resourceBytes.length == 0) {
      throw new IllegalArgumentException(
          contractLabel + " resource must not be empty: " + resourcePath);
    }
    final JsonNode document;
    try {
      document =
          Objects.requireNonNullElseGet(
              JSON_MAPPER.readTree(resourceBytes), MissingNode::getInstance);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException(
          "Failed to parse " + contractLabel + " resource: " + resourcePath, exception);
    }
    if (document.isMissingNode() || document.isNull()) {
      throw new IllegalArgumentException(
          contractLabel + " resource must not be empty: " + resourcePath);
    }
    return document;
  }

  static @Nullable JsonNode nullableField(JsonNode document, String key) {
    Objects.requireNonNull(document, "document");
    Objects.requireNonNull(key, "key");
    return document.get(key);
  }

  static JsonNode requireObject(JsonNode document, String key, String message) {
    @Nullable JsonNode value = nullableField(document, key);
    if (value == null || !value.isObject()) {
      throw new IllegalArgumentException(message);
    }
    return value;
  }

  static String requireText(JsonNode document, String key) {
    @Nullable JsonNode value = nullableField(document, key);
    String normalized =
        value == null || value.isNull() || !value.isString() ? "" : value.stringValue().trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(key + " must be a non-blank JSON string.");
    }
    return normalized;
  }

  static boolean requireBoolean(JsonNode document, String key) {
    @Nullable JsonNode value = nullableField(document, key);
    if (value == null || value.isNull() || !value.isBoolean()) {
      throw new IllegalArgumentException(key + " must be one JSON boolean.");
    }
    return value.booleanValue();
  }

  static int requireInt(JsonNode document, String key) {
    @Nullable JsonNode value = nullableField(document, key);
    if (value == null || value.isNull() || !value.canConvertToInt()) {
      throw new IllegalArgumentException(key + " must be one JSON integer.");
    }
    return value.intValue();
  }

  static List<String> optionalStringArray(JsonNode document, String key) {
    @Nullable JsonNode value = nullableField(document, key);
    if (value == null || value.isNull()) {
      return List.of();
    }
    return requireStringArrayValue(value, key);
  }

  static List<String> requireStringArray(JsonNode document, String key) {
    @Nullable JsonNode value = nullableField(document, key);
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
      String normalized = element.isString() ? element.stringValue().trim() : "";
      if (normalized.isEmpty()) {
        throw new IllegalArgumentException(key + " must be a JSON array of strings.");
      }
      values.add(normalized);
    }
    return List.copyOf(values);
  }
}
