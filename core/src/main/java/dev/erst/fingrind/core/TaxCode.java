package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable identifier for one tax treatment code. */
public record TaxCode(String value) {
  /** Normalizes and validates one tax code. */
  public TaxCode {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Tax code must not be blank.");
    }
    if (value.length() > 64) {
      throw new IllegalArgumentException("Tax code must not exceed 64 characters.");
    }
  }
}
