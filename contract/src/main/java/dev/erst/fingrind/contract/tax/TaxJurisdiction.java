package dev.erst.fingrind.contract.tax;

import java.util.Objects;

/** Jurisdiction token or label that owns one declared tax registration. */
public record TaxJurisdiction(String value) {
  private static final int MAX_LENGTH = 120;

  /** Returns the canonical maximum UTF-16 length accepted for one tax jurisdiction. */
  public static int maxLength() {
    return MAX_LENGTH;
  }

  /** Validates one tax-jurisdiction label. */
  public TaxJurisdiction {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Tax jurisdiction must not be blank.");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Tax jurisdiction must not exceed %d characters.".formatted(MAX_LENGTH));
    }
  }
}
