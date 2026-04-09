package dev.erst.fingrind.core;

import java.util.Objects;

/** Ledger account identifier attached to one journal line. */
public record AccountCode(String value) {
  /** Validates an account code without imposing a jurisdiction-specific format. */
  public AccountCode {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Account code must not be blank.");
    }
  }
}
