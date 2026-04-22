package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.Objects;

/** Command-syntax facts for one canonical public protocol operation. */
public record ProtocolCommandSignature(
    String displayLabel, List<String> aliases, List<String> options, String usage) {
  /** Validates one command-signature descriptor. */
  public ProtocolCommandSignature {
    displayLabel = requireText(displayLabel, "displayLabel");
    aliases = copyList(aliases);
    options = copyList(options);
    usage = requireText(usage, "usage");
  }

  private static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName);
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return value;
  }

  private static <T> List<T> copyList(List<T> values) {
    return values == null ? List.of() : List.copyOf(values);
  }
}
