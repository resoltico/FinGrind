package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable registration identifier for one tax registration. */
public record TaxRegistrationId(String value) {
  /** Normalizes and validates one tax-registration identifier. */
  public TaxRegistrationId {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Tax registration id must not be blank.");
    }
    if (value.length() > 128) {
      throw new IllegalArgumentException("Tax registration id must not exceed 128 characters.");
    }
  }
}
