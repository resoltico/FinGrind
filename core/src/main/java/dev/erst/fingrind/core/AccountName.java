package dev.erst.fingrind.core;

import java.util.Objects;

/** Human-readable display name for one declared ledger account. */
public record AccountName(String value) {
  /** Validates an account name without imposing jurisdiction-specific vocabulary. */
  public AccountName {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Account name must not be blank.");
    }
  }
}
