package dev.erst.fingrind.core;

import java.util.Objects;

/** Canonical display label for one tax code definition. */
public record TaxCodeName(String value) {
  /** Normalizes and validates one tax code display label. */
  public TaxCodeName {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Tax code name must not be blank.");
    }
    if (value.length() > 255) {
      throw new IllegalArgumentException("Tax code name must not exceed 255 characters.");
    }
  }
}
