package dev.erst.fingrind.core;

import java.util.Objects;

/** Canonical display name for one external counterparty. */
public record CounterpartyName(String value) {
  /** Normalizes and validates one counterparty display name. */
  public CounterpartyName {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Counterparty name must not be blank.");
    }
    if (value.length() > 255) {
      throw new IllegalArgumentException("Counterparty name must not exceed 255 characters.");
    }
  }
}
