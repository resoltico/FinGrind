package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.Objects;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Scalar node readers for protocol-owned JSON contract snapshots. */
final class JsonContractScalarReaders {
  private JsonContractScalarReaders() {}

  static String requireText(JsonNode document, String key) {
    @Nullable JsonNode value = field(document, key);
    String normalized =
        value == null || value.isNull() || !value.isString() ? "" : value.stringValue().strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(key + " must be a non-blank JSON string.");
    }
    return normalized;
  }

  static String requireExactText(JsonNode document, String key) {
    @Nullable JsonNode value = field(document, key);
    if (value == null || value.isNull() || !value.isString()) {
      throw new IllegalArgumentException(key + " must be a non-blank JSON string.");
    }
    return ContractDescriptorValidation.requireExactText(value.stringValue(), key);
  }

  static boolean requireBoolean(JsonNode document, String key) {
    @Nullable JsonNode value = field(document, key);
    if (value == null || value.isNull() || !value.isBoolean()) {
      throw new IllegalArgumentException(key + " must be one JSON boolean.");
    }
    return value.booleanValue();
  }

  static int requireInt(JsonNode document, String key) {
    @Nullable JsonNode value = field(document, key);
    OptionalInt intValue =
        value == null || value.isNull() ? OptionalInt.empty() : value.intValueOpt();
    if (intValue.isEmpty()) {
      throw new IllegalArgumentException(key + " must be one JSON integer.");
    }
    return intValue.orElseThrow();
  }

  private static @Nullable JsonNode field(JsonNode document, String key) {
    return Objects.requireNonNull(document, "document").get(Objects.requireNonNull(key, "key"));
  }
}
