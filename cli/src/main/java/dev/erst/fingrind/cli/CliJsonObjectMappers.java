package dev.erst.fingrind.cli;

import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Shared object-mapper and duplicate-key detection utilities for CLI JSON requests. */
final class CliJsonObjectMappers {
  private static final ObjectMapper LENIENT_OBJECT_MAPPER = JsonMapper.builder().build();
  private static final ObjectMapper STRICT_DUPLICATE_OBJECT_MAPPER =
      JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

  private CliJsonObjectMappers() {}

  static ObjectMapper configuredObjectMapper() {
    return JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build();
  }

  static boolean hasDuplicateObjectKeys(byte[] requestBytes) {
    Objects.requireNonNull(requestBytes, "requestBytes");
    try {
      STRICT_DUPLICATE_OBJECT_MAPPER.readTree(requestBytes);
      return false;
    } catch (JacksonException strictFailure) {
      try {
        LENIENT_OBJECT_MAPPER.readTree(requestBytes);
        return true;
      } catch (JacksonException syntaxFailure) {
        return false;
      }
    }
  }
}
