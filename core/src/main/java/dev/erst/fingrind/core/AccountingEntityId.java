package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable identifier for one accounting entity in an organization graph. */
public record AccountingEntityId(String value) {
  /** Normalizes and validates one accounting-entity identifier. */
  public AccountingEntityId {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Accounting entity id must not be blank.");
    }
    if (value.length() > 128) {
      throw new IllegalArgumentException("Accounting entity id must not exceed 128 characters.");
    }
  }
}
