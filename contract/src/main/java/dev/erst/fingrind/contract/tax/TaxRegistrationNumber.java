package dev.erst.fingrind.contract.tax;

import java.util.Objects;

/** Optional operator-managed registration number inside one tax jurisdiction. */
public record TaxRegistrationNumber(String value) {
  private static final int MAX_LENGTH = 120;

  /** Returns the canonical maximum UTF-16 length accepted for one tax-registration number. */
  public static int maxLength() {
    return MAX_LENGTH;
  }

  /** Validates one tax-registration number. */
  public TaxRegistrationNumber {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Tax registration number must not be blank.");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Tax registration number must not exceed %d characters.".formatted(MAX_LENGTH));
    }
  }
}
