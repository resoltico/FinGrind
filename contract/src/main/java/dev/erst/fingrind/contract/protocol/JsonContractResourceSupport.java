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
    try {
      return Objects.requireNonNull(JSON_MAPPER.readTree(resourceBytes), contractLabel);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException(
          "Failed to parse " + contractLabel + " resource: " + resourcePath, exception);
    }
  }

  static String requireText(JsonNode document, String key) {
    Objects.requireNonNull(document, "document");
    Objects.requireNonNull(key, "key");
    JsonNode value = document.get(key);
    String normalized =
        value == null || value.isNull() || !value.isString() ? "" : value.stringValue().trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(key + " must be a non-blank JSON string.");
    }
    return normalized;
  }

  static List<String> optionalStringArray(JsonNode document, String key) {
    Objects.requireNonNull(document, "document");
    Objects.requireNonNull(key, "key");
    JsonNode value = document.get(key);
    if (value == null || value.isNull()) {
      return List.of();
    }
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
