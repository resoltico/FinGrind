package dev.erst.fingrind.contract.tax;

import java.util.Objects;

/** Human-facing display name for one declared tax registration. */
public record TaxRegistrationName(String value) {
  private static final int MAX_LENGTH = 200;

  /** Returns the canonical maximum UTF-16 length accepted for one tax-registration name. */
  public static int maxLength() {
    return MAX_LENGTH;
  }

  /** Validates one tax-registration display name. */
  public TaxRegistrationName {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Tax registration name must not be blank.");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Tax registration name must not exceed %d characters.".formatted(MAX_LENGTH));
    }
  }
}
