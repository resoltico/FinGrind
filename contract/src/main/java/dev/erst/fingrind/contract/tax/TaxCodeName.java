package dev.erst.fingrind.contract.tax;

import java.util.Objects;

/** Human-facing display name for one declared tax code. */
public record TaxCodeName(String value) {
  private static final int MAX_LENGTH = 200;

  /** Validates one tax-code display name. */
  public TaxCodeName {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Tax code name must not be blank.");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Tax code name must not exceed %d characters.".formatted(MAX_LENGTH));
    }
  }
}
