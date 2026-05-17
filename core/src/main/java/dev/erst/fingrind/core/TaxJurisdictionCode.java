package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable neutral identifier for one tax jurisdiction. */
public record TaxJurisdictionCode(String value) {
  /** Normalizes and validates one tax-jurisdiction code. */
  public TaxJurisdictionCode {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Tax jurisdiction code must not be blank.");
    }
    if (value.length() > 64) {
      throw new IllegalArgumentException("Tax jurisdiction code must not exceed 64 characters.");
    }
  }
}
