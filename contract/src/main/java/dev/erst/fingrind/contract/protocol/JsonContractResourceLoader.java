package dev.erst.fingrind.contract.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.ObjectNode;

/** Resource-loading helpers for protocol-owned JSON contract snapshots. */
final class JsonContractResourceLoader {
  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  private JsonContractResourceLoader() {}

  static ObjectNode loadObject(
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
    return (ObjectNode) document;
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
}
