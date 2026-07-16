package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;

/** Stable caller-chosen identifier for one capitalized fixed-asset lifecycle. */
public record FixedAssetId(String value) {
  private static final int MAX_LENGTH = 120;
  private static final String PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";

  /** Validates one stable fixed-asset identifier. */
  public FixedAssetId {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Fixed-asset id must not be blank.");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Fixed-asset id must not exceed %d characters.".formatted(MAX_LENGTH));
    }
    if (!value.matches(PATTERN)) {
      throw new IllegalArgumentException("Fixed-asset id must use lowercase kebab-case tokens.");
    }
  }
}
