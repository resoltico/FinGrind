package dev.erst.fingrind.core.attestation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Reads committed raw corpus resources without permitting test-time fixture construction. */
final class AttestationStaticCorpusResourceLoader {
  private static final String ROOT = "/dev/erst/fingrind/core/attestation/corpus/";

  private AttestationStaticCorpusResourceLoader() {}

  static byte[] base64(String path) {
    return Base64.getDecoder().decode(text(path));
  }

  static Map<String, String> fields(String path) {
    Map<String, String> fields =
        text(path)
            .lines()
            .map(line -> field(path, line))
            .collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (firstValue, secondValue) -> {
                      throw new IllegalStateException(
                          "Static corpus metadata is malformed: " + path);
                    }));
    if (!fields.keySet().equals(Set.of("base", "offset", "replacedByteCount", "sourceSha256"))) {
      throw new IllegalStateException("Static corpus metadata has an unexpected schema: " + path);
    }
    return fields;
  }

  static String text(String path) {
    try (InputStream input =
        AttestationStaticCorpusResourceLoader.class.getResourceAsStream(ROOT + path)) {
      if (input == null) {
        throw new IllegalStateException("Static corpus resource is missing: " + path);
      }
      return new String(input.readAllBytes(), StandardCharsets.US_ASCII).trim();
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot read static corpus resource: " + path, exception);
    }
  }

  private static Map.Entry<String, String> field(String path, String line) {
    int separator = line.indexOf('=');
    if (separator <= 0) {
      throw new IllegalStateException("Static corpus metadata is malformed: " + path);
    }
    return Map.entry(line.substring(0, separator), line.substring(separator + 1));
  }
}
